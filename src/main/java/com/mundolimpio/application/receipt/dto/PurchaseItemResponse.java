package com.mundolimpio.application.receipt.dto;

import java.math.BigDecimal;

/**
 * DTO de salida que representa un item individual de una compra confirmada.
 * Se incluye dentro de PurchaseResponse.items.
 * 
 * POR QUÉ bulkProductId nullable:
 * - Si el admin no matcheó el producto con el catálogo, queda null.
 * - La compra se persiste igual, solo que sin link a BulkProduct.
 */
public record PurchaseItemResponse(
        Long id,                  // ID del PurchaseItem persistido
        String description,       // Descripción del producto
        Integer quantity,         // Cantidad comprada
        BigDecimal unitPrice,     // Precio unitario
        BigDecimal totalPrice,    // Precio total de la línea
        Long bulkProductId        // ID del BulkProduct matcheado, o null
) {}
