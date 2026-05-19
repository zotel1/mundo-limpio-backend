package com.mundolimpio.application.receipt.dto;

import java.util.List;

/**
 * WHAT: Resultado del procesamiento OCR de una imagen de ticket.
 * WHY: Encapsula tanto el texto crudo (para debugging) como las líneas estructuradas
 *      (para mostrar al admin). Es un DTO interno entre ReceiptOcrService y ReceiptProcessingService.
 * 
 * rawText: texto completo extraído por Tesseract (para logging y debugging).
 * lines: líneas de producto parseadas del rawText con sus confidences.
 */
public record OcrResult(
        String rawText,                  // Texto crudo extraído por Tesseract
        List<ProductLineDto> lines       // Líneas de producto estructuradas
) {}
