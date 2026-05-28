package com.mundolimpio.application.receipt.exception;

/**
 * WHAT: Excepción lanzada cuando el OCR no puede procesar una imagen de ticket.
 * WHY: Necesitamos un tipo específico para que el handler pueda mapear a 422 Unprocessable Entity.
 *       Sin una excepción tipada, caería al catch-all de Exception → 500 (incorrecto).
 * 
 * Se lanza en ReceiptProcessingService cuando:
 * - Tesseract no detecta texto en la imagen (imagen borrosa, ilegible).
 * - Todas las líneas detectadas tienen confidence < 0.3.
 */
public class OcrProcessingException extends RuntimeException {

    /**
     * @param message Descripción del error de OCR (ej: "No text detected in image")
     */
    public OcrProcessingException(String message) {
        super(message);
    }
}
