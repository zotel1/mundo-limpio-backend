package com.mundolimpio.application.receipt.dto;

import java.math.BigDecimal;

/**
 * DTO que representa una línea de producto detectada por OCR en el ticket.
 * 
 * POR QUÉ un record:
 * - Inmutable por defecto (los campos son final).
 * - Spring serializa automáticamente a JSON.
 * - bulkProductId nullable: el OCR puede no matchear un producto del catálogo.
 * - confidence es un double entre 0 y 1 (0 = nada confiable, 1 = 100% seguro).
 */
public record ProductLineDto(
        String name,              // Nombre del producto detectado por OCR
        Integer quantity,         // Cantidad comprada detectada
        BigDecimal unitPrice,     // Precio unitario detectado
        double confidence,        // Nivel de confianza del OCR (0.0 a 1.0)
        Long bulkProductId        // ID del BulkProduct matcheado, o null si no se reconoce
) {}
