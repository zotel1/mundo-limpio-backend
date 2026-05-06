package com.mundolimpio.application.bulkproduct.dto;

import java.math.BigDecimal;

/**
 * Record para la petición de creación/actualización de materia prima.
 *
 * @param name Nombre del producto (ej: "Cloro Puro", "Detergente Base")
 * @param currentStockLiters Stock actual en litros
 * @param costPerLiter Costo por litro de materia prima
 * @param conversionRatio Ratio de conversión (ej: 4 para cloro 1:4)
 */
public record BulkProductRequest(
        String name,
        BigDecimal currentStockLiters,
        BigDecimal costPerLiter,
        BigDecimal conversionRatio
) {
}
