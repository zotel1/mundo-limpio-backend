package com.mundolimpio.application.receipt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de respuesta del endpoint POST /api/v1/receipts/confirm.
 * Retorna la compra completa después de ser persistida.
 */
public record PurchaseResponse(
        Long id,                              // ID de la compra creada
        String imageUrl,                      // URL de la imagen del ticket
        String supplierName,                  // Nombre del proveedor
        LocalDate purchaseDate,               // Fecha de la compra
        BigDecimal total,                     // Monto total (suma de items)
        List<PurchaseItemResponse> items      // Detalle de items comprados
) {}
