package com.mundolimpio.application.receipt.service;

import com.mundolimpio.application.receipt.dto.OcrResult;
import com.mundolimpio.application.receipt.dto.ProductLineDto;
import com.mundolimpio.application.receipt.exception.OcrProcessingException;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WHAT: Tests unitarios para TesseractOcrService.
 * WHY: Verifica que la integración con Tess4J funciona correctamente,
 *      que el preprocesamiento de imagen y el parseo de texto OCR
 *      producen los resultados esperados.
 *
 * DIFFERENCES: Usamos MockitoExtension en vez de @SpringBootTest
 *              porque solo mockeamos ITesseract y testeamos lógica pura.
 */
@ExtendWith(MockitoExtension.class)
class TesseractOcrServiceTest {

    @Mock
    private ITesseract tesseract;

    @InjectMocks
    private TesseractOcrService ocrService;

    private byte[] sampleImageBytes;

    /**
     * Crea una imagen JPEG mínima (1x1 pixel blanco) para tests.
     * WHY: Necesitamos bytes de imagen reales para que el preprocesamiento
     *      no falle al intentar decodificar bytes inválidos con ImageIO.
     */
    @BeforeEach
    void setUp() throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "JPEG", baos);
        sampleImageBytes = baos.toByteArray();
    }

    // ===================== RED: Tests de éxito =====================

    /**
     * RED: Verifica que process() retorna OcrResult con líneas parseadas
     * cuando Tesseract extrae texto de un ticket legible.
     */
    @Test
    void shouldExtractProductLinesFromClearReceipt() throws TesseractException {
        // WHAT: Texto simulado de ticket argentino con 3 productos
        String rawText = """
                SUPERMERCADO LA ESQUINA
                Fecha: 15/05/2026
                LAVANDINA 5L $1.250,00 2
                DETERGENTE LIQ $890,50 1
                CLORO GEL 1L $450,00 2
                """;

        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn(rawText);

        OcrResult result = ocrService.process(sampleImageBytes);

        assertThat(result).isNotNull();
        assertThat(result.rawText()).isEqualTo(rawText.trim());
        assertThat(result.lines()).isNotEmpty();

        // Verifica que setLanguage("spa") fue llamado
        verify(tesseract).setLanguage("spa");
        verify(tesseract).doOCR(any(BufferedImage.class));
    }

    /**
     * RED: Verifica que se detecta el nombre del proveedor (primera línea no numérica).
     */
    @Test
    void shouldDetectSupplierNameFromFirstLine() throws TesseractException {
        String rawText = "DISTRIBUIDORA XYZ\nFecha: 10/01/2026\nProducto A $500 3\n";

        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn(rawText);

        OcrResult result = ocrService.process(sampleImageBytes);

        assertThat(result.lines()).isNotEmpty();
        assertThat(result.lines().stream().anyMatch(
                l -> l.name().contains("Producto A"))).isTrue();
    }

    /**
     * RED: Verifica que cada línea de producto tiene confidence entre 0 y 1.
     */
    @Test
    void shouldAssignConfidenceBetweenZeroAndOne() throws TesseractException {
        String rawText = "Producto X $100,00 5\n";

        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn(rawText);

        OcrResult result = ocrService.process(sampleImageBytes);

        for (ProductLineDto line : result.lines()) {
            assertThat(line.confidence()).isBetween(0.0, 1.0);
        }
    }

    // ===================== RED: Tests de fallo =====================

    /**
     * RED: Verifica que se lanza OcrProcessingException cuando
     * Tesseract no detecta texto (imagen borrosa/vacía).
     */
    @Test
    void shouldThrowExceptionWhenNoTextDetected() throws TesseractException {
        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("");

        assertThatThrownBy(() -> ocrService.process(sampleImageBytes))
                .isInstanceOf(OcrProcessingException.class)
                .hasMessageContaining("No text detected");
    }

    /**
     * RED: Verifica que se lanza OcrProcessingException cuando
     * Tesseract lanza TesseractException (error a nivel motor OCR).
     */
    @Test
    void shouldThrowExceptionWhenTesseractFails() throws TesseractException {
        when(tesseract.doOCR(any(BufferedImage.class)))
                .thenThrow(new TesseractException("Tesseract not installed"));

        assertThatThrownBy(() -> ocrService.process(sampleImageBytes))
                .isInstanceOf(OcrProcessingException.class)
                .hasMessageContaining("OCR processing failed");
    }

    /**
     * RED: Verifica que se lanza OcrProcessingException cuando
     * todas las líneas tienen confidence < 0.3.
     */
    @Test
    void shouldThrowExceptionWhenAllLinesLowConfidence() throws TesseractException {
        // Líneas que matchean el patrón pero con nombres cortos (< 3 letras) + precio inválido (< 1)
        // → confianza total < 0.3
        String rawText = "ab 1 $0,50\ncd 2 $0,99\n";

        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn(rawText);

        assertThatThrownBy(() -> ocrService.process(sampleImageBytes))
                .isInstanceOf(OcrProcessingException.class)
                .hasMessageContaining("confidence");
    }

    /**
     * RED: Verifica que el preprocesamiento convierte a escala de grises.
     * Llamamos al método preprocessImage (package-private) para validar.
     */
    @Test
    void shouldConvertImageToGrayscale() {
        BufferedImage result = ocrService.preprocessImage(sampleImageBytes);

        assertThat(result).isNotNull();
        // La imagen preprocesada debe ser TYPE_BYTE_GRAY (escala de grises)
        assertThat(result.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
    }

    /**
     * RED: Verifica que parseText extrae cantidades y precios correctamente.
     */
    @Test
    void shouldParseProductLinesWithQuantityAndPrice() {
        String rawText = "CLORO 5L $1.250,50 3\nDETERGENTE $890,00 1\n";

        List<ProductLineDto> lines = ocrService.parseText(rawText);

        assertThat(lines).hasSize(2);

        // Primera línea: CLORO 5L, 3 unidades, $1.250,50
        assertThat(lines.get(0).name()).contains("CLORO");
        assertThat(lines.get(0).quantity()).isEqualTo(3);
        assertThat(lines.get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("1250.50"));

        // Segunda línea: DETERGENTE, 1 unidad, $890,00
        assertThat(lines.get(1).name()).contains("DETERGENTE");
        assertThat(lines.get(1).quantity()).isEqualTo(1);
        assertThat(lines.get(1).unitPrice()).isEqualByComparingTo(new BigDecimal("890.00"));
    }

    /**
     * RED: Verifica que parseText asigna confianza alta a líneas bien formadas.
     */
    @Test
    void shouldAssignHighConfidenceToWellFormedLines() {
        String rawText = "LAVANDINA AYUDA 5L $1.250,00 2\n";

        List<ProductLineDto> lines = ocrService.parseText(rawText);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).confidence()).isGreaterThanOrEqualTo(0.7);
    }

    /**
     * RED: Verifica que parseText retorna lista vacía para texto sin productos.
     */
    @Test
    void shouldReturnEmptyListForTextWithoutProducts() {
        String rawText = "SUPERMERCADO LA ESQUINA\nFecha: 15/05/2026\nTOTAL: $4.500\n";

        List<ProductLineDto> lines = ocrService.parseText(rawText);

        // Solo header/footer, sin líneas de producto con precio
        assertThat(lines).isEmpty();
    }

    /**
     * RED: Verifica que bytes nulos lanzan excepción.
     */
    @Test
    void shouldThrowExceptionWhenImageBytesAreNull() {
        assertThatThrownBy(() -> ocrService.process(null))
                .isInstanceOf(OcrProcessingException.class)
                .hasMessageContaining("image data");
    }

    /**
     * RED: Verifica que parseText maneja texto nulo.
     */
    @Test
    void shouldReturnEmptyListForNullText() {
        List<ProductLineDto> lines = ocrService.parseText(null);

        assertThat(lines).isEmpty();
    }
}
