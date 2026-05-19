package com.mundolimpio.application.receipt.service;

import com.mundolimpio.application.receipt.dto.OcrResult;

/**
 * WHAT: Interfaz que define el contrato para el servicio de OCR.
 * WHY: Abstracción sobre Tesseract/Tess4J permite cambiar el motor de OCR
 *      (ej: Google Cloud Vision, AWS Textract) sin tocar el código que lo usa.
 * 
 * IMPLEMENTACIÓN: TesseractOcrService (PR 2) — usa Tess4J con language pack español.
 */
public interface ReceiptOcrService {

    /**
     * Procesa una imagen y extrae texto estructurado con líneas de producto.
     * 
     * @param imageData Bytes de la imagen (JPEG/PNG) del ticket de compra
     * @return OcrResult con texto crudo y líneas de producto detectadas
     * @throws com.mundolimpio.application.receipt.exception.OcrProcessingException
     *         si no se detecta texto o la confianza es muy baja
     */
    OcrResult process(byte[] imageData);
}
