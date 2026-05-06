package com.mundolimpio.application.bulkproduct.dto;

import java.math.BigDecimal;

/**
 * Record para la respuesta de materia prima.
 *
 * @param id Identificador único
 * @param name Nombre del producto
 * @param currentStockLiters Stock actual en litros
 * @param costPerLiter Costo por litro
 * @param conversionRatio Ratio de conversión
 */
public record BulkProductResponse(
        Long id,
        String name,
        BigDecimal currentStockLiters,
        BigDecimal costPerLiter,
        BigDecimal conversionRatio
) {
}
