package com.mundolimpio.application.receipt.service;

import com.mundolimpio.application.receipt.dto.OcrResult;
import com.mundolimpio.application.receipt.dto.ProductLineDto;
import com.mundolimpio.application.receipt.dto.ReceiptProcessResponse;
import com.mundolimpio.application.receipt.exception.OcrProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WHAT: Tests unitarios para ReceiptProcessingService.
 * WHY: Verifica la orquestación del pipeline OCR completo:
 *      upload a storage → OCR → construcción de ReceiptProcessResponse.
 *      Mockeamos los servicios externos para aislar la lógica de orquestación.
 *
 * DIFFERENCES: Usamos MockitoExtension (sin Spring) porque solo mockeamos
 *              ReceiptStorageService y ReceiptOcrService.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptProcessingServiceTest {

    @Mock
    private ReceiptStorageService storageService;

    @Mock
    private ReceiptOcrService ocrService;

    @InjectMocks
    private ReceiptProcessingService processingService;

    private MultipartFile validImage;
    private byte[] imageBytes;
    private OcrResult sampleOcrResult;

    @BeforeEach
    void setUp() {
        imageBytes = new byte[]{0x01, 0x02, 0x03};
        validImage = new MockMultipartFile(
                "image", "ticket.jpg", "image/jpeg", imageBytes);

        List<ProductLineDto> lines = List.of(
                new ProductLineDto("Lavandina 5L", 2,
                        new BigDecimal("1250.50"), 0.95, null),
                new ProductLineDto("Detergente Líquido", 1,
                        new BigDecimal("890.00"), 0.90, null)
        );
        sampleOcrResult = new OcrResult(
                "SUPERMERCADO LA ESQUINA\nFecha: 15/05/2026\n"
                        + "Lavandina 5L $1.250,50 2\nDetergente Líquido $890,00 1",
                lines
        );
    }

    // ===================== RED: Tests de procesamiento exitoso =====================

    /**
     * RED: Verifica que processReceipt() retorna ReceiptProcessResponse
     * con todos los campos poblados correctamente.
     */
    @Test
    void shouldReturnReceiptProcessResponseWithAllFields() throws Exception {
        when(storageService.upload(validImage))
                .thenReturn("https://testproject.supabase.co/storage/v1/object/public/receipts/ticket-123.jpg");
        when(ocrService.process(imageBytes)).thenReturn(sampleOcrResult);

        ReceiptProcessResponse response = processingService.processReceipt(validImage);

        assertThat(response).isNotNull();
        assertThat(response.imageUrl()).contains("ticket-123");
        assertThat(response.lines()).hasSize(2);
        assertThat(response.detectedSupplier()).isNotNull();
        assertThat(response.detectedDate()).contains("15/05/2026");
    }

    /**
     * RED: Verifica que el storage es llamado antes que el OCR.
     * WHY: La imagen debe estar almacenada antes de procesar (para auditoría).
     */
    @Test
    void shouldUploadBeforeOcr() throws Exception {
        when(storageService.upload(validImage))
                .thenReturn("https://testproject.supabase.co/storage/v1/object/public/receipts/ticket-123.jpg");
        when(ocrService.process(imageBytes)).thenReturn(sampleOcrResult);

        processingService.processReceipt(validImage);

        // Verificar orden de llamadas: storage primero, OCR después
        var inOrder = inOrder(storageService, ocrService);
        inOrder.verify(storageService).upload(validImage);
        inOrder.verify(ocrService).process(imageBytes);
    }

    /**
     * RED: Verifica que las líneas del OcrResult se pasan directamente al response.
     */
    @Test
    void shouldPassProductLinesToResponse() throws Exception {
        when(storageService.upload(validImage)).thenReturn("https://example.com/img.jpg");
        when(ocrService.process(imageBytes)).thenReturn(sampleOcrResult);

        ReceiptProcessResponse response = processingService.processReceipt(validImage);

        assertThat(response.lines()).hasSize(2);
        assertThat(response.lines().get(0).name()).isEqualTo("Lavandina 5L");
        assertThat(response.lines().get(0).confidence()).isEqualTo(0.95);
        assertThat(response.lines().get(1).name()).isEqualTo("Detergente Líquido");
    }

    /**
     * RED: Verifica que se detecta el nombre del proveedor del texto OCR.
     */
    @Test
    void shouldDetectSupplierNameFromOcrText() throws Exception {
        when(storageService.upload(validImage)).thenReturn("https://example.com/img.jpg");
        when(ocrService.process(imageBytes)).thenReturn(sampleOcrResult);

        ReceiptProcessResponse response = processingService.processReceipt(validImage);

        // La primera línea no vacía que no es fecha ni total → "SUPERMERCADO LA ESQUINA"
        assertThat(response.detectedSupplier()).isEqualTo("SUPERMERCADO LA ESQUINA");
    }

    /**
     * RED: Verifica que se extrae la fecha del ticket del texto OCR.
     */
    @Test
    void shouldDetectDateFromOcrText() throws Exception {
        when(storageService.upload(validImage)).thenReturn("https://example.com/img.jpg");
        OcrResult resultWithDate = new OcrResult(
                "PROVEEDOR ABC\nFecha: 25/12/2025\nProducto X $100 1",
                List.of(new ProductLineDto("Producto X", 1,
                        new BigDecimal("100.00"), 0.8, null))
        );
        when(ocrService.process(imageBytes)).thenReturn(resultWithDate);

        ReceiptProcessResponse response = processingService.processReceipt(validImage);

        assertThat(response.detectedDate()).isEqualTo("25/12/2025");
    }

    /**
     * RED: Verifica que detectedDate es null cuando no hay fecha en el texto.
     */
    @Test
    void shouldReturnNullDateWhenNoDateFound() throws Exception {
        when(storageService.upload(validImage)).thenReturn("https://example.com/img.jpg");
        OcrResult resultNoDate = new OcrResult(
                "PROVEEDOR XYZ\nProducto A $500 2",
                List.of(new ProductLineDto("Producto A", 2,
                        new BigDecimal("500.00"), 0.8, null))
        );
        when(ocrService.process(imageBytes)).thenReturn(resultNoDate);

        ReceiptProcessResponse response = processingService.processReceipt(validImage);

        assertThat(response.detectedDate()).isNull();
    }

    // ===================== RED: Tests de fallo =====================

    /**
     * RED: Verifica que se propaga OcrProcessingException del OCR service.
     */
    @Test
    void shouldPropagateOcrProcessingException() throws Exception {
        when(storageService.upload(validImage)).thenReturn("https://example.com/img.jpg");
        when(ocrService.process(imageBytes))
                .thenThrow(new OcrProcessingException("Cannot read text"));

        assertThatThrownBy(() -> processingService.processReceipt(validImage))
                .isInstanceOf(OcrProcessingException.class)
                .hasMessageContaining("Cannot read text");
    }

    /**
     * RED: Verifica que se lanza excepción cuando falla el storage.
     */
    @Test
    void shouldThrowExceptionWhenStorageFails() throws Exception {
        when(storageService.upload(validImage))
                .thenThrow(new RuntimeException("Storage unavailable"));

        assertThatThrownBy(() -> processingService.processReceipt(validImage))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Storage unavailable");
    }

    /**
     * RED: Verifica que el OCR NO se ejecuta si el storage falla.
     * WHY: Si no podemos guardar la imagen, no tiene sentido procesarla.
     */
    @Test
    void shouldNotRunOcrIfStorageFails() throws Exception {
        when(storageService.upload(validImage))
                .thenThrow(new RuntimeException("Storage unavailable"));

        try {
            processingService.processReceipt(validImage);
        } catch (RuntimeException e) {
            // esperado
        }

        verify(ocrService, never()).process(any());
    }
}
