package com.mundolimpio.application.receipt.service;

import com.mundolimpio.application.receipt.dto.OcrResult;
import com.mundolimpio.application.receipt.dto.ReceiptProcessResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WHAT: Servicio que orquesta el pipeline de procesamiento de tickets.
 * WHY: Coordina las dos operaciones externas (storage + OCR) en orden,
 *      extrayendo información adicional del texto OCR (proveedor, fecha)
 *      y construyendo el DTO de respuesta.
 * 
 * FLUJO:
 * 1. Recibir MultipartFile (imagen del ticket)
 * 2. Subir imagen a Supabase Storage → obtener URL
 * 3. Extraer texto de la imagen via OCR (Tesseract)
 * 4. Extraer nombre del proveedor y fecha del texto OCR
 * 5. Construir y retornar ReceiptProcessResponse
 * 
 * DIFFERENCES con ReceiptConfirmationService (PR 3):
 * - Este servicio NO persiste nada en la base de datos.
 * - Este servicio NO busca ni crea Suppliers.
 * - Solo extrae datos de la imagen para que el admin los revise.
 */
@Service
public class ReceiptProcessingService {

    private final ReceiptStorageService storageService;
    private final ReceiptOcrService ocrService;

    /**
     * Inyección por constructor de los servicios colaboradores.
     */
    public ReceiptProcessingService(ReceiptStorageService storageService,
                                     ReceiptOcrService ocrService) {
        this.storageService = storageService;
        this.ocrService = ocrService;
    }

    /**
     * Procesa una imagen de ticket de compra: la sube a storage y extrae datos via OCR.
     *
     * @param image Archivo multipart (JPEG/PNG) con la foto del ticket
     * @return ReceiptProcessResponse con URL de la imagen, líneas de producto,
     *         proveedor detectado y fecha detectada
     * @throws OcrProcessingException si el OCR no puede extraer texto
     * @throws RuntimeException si falla la subida a storage
     */
    public ReceiptProcessResponse processReceipt(MultipartFile image) {
        // Paso 1: Subir imagen a Supabase Storage
        String imageUrl = storageService.upload(image);

        // Paso 2: Extraer texto de la imagen via OCR
        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image bytes", e);
        }

        OcrResult ocrResult = ocrService.process(imageBytes);

        // Paso 3: Extraer metadatos del texto OCR
        String detectedSupplier = extractSupplierName(ocrResult.rawText());
        String detectedDate = extractDate(ocrResult.rawText());

        // Paso 4: Construir respuesta
        return new ReceiptProcessResponse(
                detectedSupplier,
                detectedDate,
                ocrResult.lines(),
                imageUrl
        );
    }

    /**
     * Extrae el nombre del proveedor del texto crudo del OCR.
     * 
     * WHAT: Toma la primera línea no vacía que no sea header/footer del ticket.
     * WHY: En la mayoría de los tickets argentinos, el nombre del comercio
     *      aparece en la primera línea del ticket (ej: "SUPERMERCADO LA ESQUINA").
     * 
     * HEURÍSTICA: Primera línea que no contiene "fecha", "total", "iva", etc.
     * 
     * @param rawText Texto crudo extraído por Tesseract
     * @return Nombre del proveedor detectado, o cadena vacía si no se detecta
     */
    private String extractSupplierName(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String[] lines = rawText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            String upper = trimmed.toUpperCase();
            // Saltar líneas que contienen palabras clave de header/footer
            if (upper.contains("FECHA") || upper.contains("TOTAL")
                    || upper.contains("SUBTOTAL") || upper.contains("IVA")
                    || upper.contains("CUIT") || upper.contains("GRACIAS")
                    || upper.contains("VUELVA") || upper.contains("HORA")
                    || trimmed.matches(".*\\$[\\d.,]+.*") // contiene precio → es producto
            ) {
                continue;
            }

            return trimmed;
        }

        return "";
    }

    /**
     * Extrae la fecha del ticket del texto crudo del OCR.
     * 
     * WHAT: Busca patrones de fecha como "Fecha: DD/MM/YYYY" o "DD/MM/YYYY".
     * WHY: Los tickets argentinos suelen incluir la fecha en formato DD/MM/YYYY.
     * 
     * FORMATOS SOPORTADOS:
     * - "Fecha: 15/05/2026"
     * - "15/05/2026"
     * - "15-05-2026"
     * 
     * @param rawText Texto crudo extraído por Tesseract
     * @return Fecha detectada como String, o null si no se encuentra
     */
    private String extractDate(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        // Patrón: "Fecha: DD/MM/YYYY" o "Fecha DD/MM/YYYY"
        Pattern dateLabelPattern = Pattern.compile(
                "(?i)fecha\\s*:?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})");
        Matcher dateLabelMatcher = dateLabelPattern.matcher(rawText);
        if (dateLabelMatcher.find()) {
            return dateLabelMatcher.group(1);
        }

        // Patrón: fecha suelta en formato DD/MM/YYYY
        Pattern datePattern = Pattern.compile(
                "\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b");
        Matcher dateMatcher = datePattern.matcher(rawText);
        if (dateMatcher.find()) {
            return dateMatcher.group(1);
        }

        return null;
    }
}
