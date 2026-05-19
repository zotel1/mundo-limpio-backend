package com.mundolimpio.application.receipt.dto;

import java.util.List;

/**
 * DTO de respuesta del endpoint POST /api/v1/receipts/process.
 * Retorna los datos extraídos por OCR para que el admin los revise.
 * 
 * POR QUÉ detectedDate es String:
 * - El OCR puede extraer fechas en formatos no estándar (ej: "15/05/26", "15-MAY-2026").
 * - El admin revisa y convierte a LocalDate en el frontend antes de enviar el confirm.
 */
public record ReceiptProcessResponse(
        String detectedSupplier,          // Nombre del proveedor detectado por OCR
        String detectedDate,              // Fecha del ticket detectada (puede ser null)
        List<ProductLineDto> lines,       // Líneas de producto detectadas
        String imageUrl                   // URL de la imagen en Supabase Storage
) {}
