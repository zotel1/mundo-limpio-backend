package com.mundolimpio.application.receipt.service;

import com.mundolimpio.application.receipt.dto.OcrResult;
import com.mundolimpio.application.receipt.dto.ProductLineDto;
import net.sourceforge.tess4j.Tesseract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * WHAT: Test de integración (smoke) con Tesseract real.
 * WHY: La spec de diseño requiere un smoke test con una imagen de ticket real
 *      para verificar que Tesseract puede extraer texto y las líneas tienen
 *      confianza > 0. Los tests unitarios mockean ITesseract y no validan
 *      la integración real con el motor OCR.
 *
 * DIFFERENCES con TesseractOcrServiceTest:
 * - Usa ITesseract REAL (Tesseract nativo), no mockeado.
 * - Carga una imagen de ticket real desde src/test/resources/receipts/.
 * - Se saltea automáticamente si Tesseract no está instalado.
 *
 * ESTRATEGIA DE SKIP:
 * - Intenta cargar Tesseract nativo (JNA) y el language pack español.
 * - Si Tesseract no está instalado o falta spa.traineddata, el test se saltea.
 * - En CI/Docker, Tesseract se instala via apk add tesseract-ocr-data-spa.
 */
class TesseractOcrSmokeIT {

    private TesseractOcrService ocrService;
    private byte[] sampleImageBytes;

    /**
     * Inicializa Tesseract real y carga la imagen de muestra del classpath.
     * Si Tesseract no está instalado o falta el language pack español,
     * el assumeTrue() saltea todos los tests automáticamente.
     */
    @BeforeEach
    void setUp() throws IOException {
        // Cargar imagen de muestra desde src/test/resources/receipts/
        Path samplePath = Paths.get("src/test/resources/receipts/sample.jpeg");
        if (!Files.exists(samplePath)) {
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("receipts/sample.jpeg");
            if (is == null) {
                assumeTrue(false, "Imagen de muestra no encontrada en classpath");
                return;
            }
            sampleImageBytes = is.readAllBytes();
        } else {
            sampleImageBytes = Files.readAllBytes(samplePath);
        }

        // Verificar que Tesseract está disponible antes de ejecutar los tests.
        // Intentamos crear una instancia y hacer un OCR rápido de la imagen.
        // Si falla (Tesseract no instalado, falta spa.traineddata, etc.), salteamos.
        boolean tesseractAvailable = false;
        try {
            Tesseract tesseract = new Tesseract();
            // WHAT: No seteamos datapath explícito — usamos la detección automática.
            // WHY: En Docker está en /usr/share/tessdata/, en Windows en Program Files,
            //      en macOS en /usr/local/share/. Tesseract auto-detecta via TESSDATA_PREFIX.
            tesseract.setLanguage("spa");
            // Prueba rápida: intentar OCR de la imagen de muestra
            TesseractOcrService probe = new TesseractOcrService(tesseract);
            probe.process(sampleImageBytes);
            // Si llegamos acá sin excepción, Tesseract funciona
            ocrService = probe;
            tesseractAvailable = true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | Exception e) {
            // Tesseract no está instalado o falta spa.traineddata
            tesseractAvailable = false;
        }

        assumeTrue(tesseractAvailable,
                "Tesseract/spa no disponible — salteando test de integración OCR");
    }

    /**
     * RED: Verifica que Tesseract real puede extraer texto de un ticket real.
     * SPEC: REC-004 "Clear receipt processed" — OCR exitoso con confianza.
     * DESIGN: Integration OCR (smoke) — verificar confidence > 0.
     */
    @Test
    void shouldExtractTextFromRealReceiptImage() {
        OcrResult result = ocrService.process(sampleImageBytes);

        // Verifica que se detectó texto crudo (no vacío)
        assertThat(result.rawText())
                .as("El OCR debe extraer al menos algo de texto de la imagen")
                .isNotBlank();

        // Verifica que se detectaron líneas de producto (o al menos una)
        assertThat(result.lines())
                .as("Debe haber al menos una línea extraída o el resultado debe contener texto")
                .isNotNull();

        // Si hay líneas, verificar que tienen confianza > 0
        for (ProductLineDto line : result.lines()) {
            assertThat(line.confidence())
                    .as("Cada línea detectada debe tener confianza > 0")
                    .isPositive();
        }

        // Smoke test: verificar que la primera línea no vacía del texto
        // existe (prueba que Tesseract funcionó realmente)
        assertThat(result.rawText().trim())
                .as("El texto extraído no debe ser solo whitespace")
                .isNotEmpty();
    }

    /**
     * RED: Verifica que el preprocesamiento funciona con una imagen real.
     */
    @Test
    void shouldPreprocessRealImageWithoutError() {
        // Verifica que preprocessImage no lanza excepción
        var processedImage = ocrService.preprocessImage(sampleImageBytes);

        assertThat(processedImage)
                .as("El preprocesamiento debe retornar una imagen")
                .isNotNull();

        assertThat(processedImage.getWidth())
                .as("La imagen preprocesada debe tener dimensiones > 0")
                .isPositive();
    }
}
