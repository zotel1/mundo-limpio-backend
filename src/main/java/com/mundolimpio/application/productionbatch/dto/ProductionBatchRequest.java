package com.mundolimpio.application.productionbatch.dto;

import java.math.BigDecimal;

/**
 * Record para la petición de creación de un lote de producción.
 *
 * @param productId ID del producto terminado (ej: Lavandina 3L)
 * @param bulkProductId ID de la materia prima usada (ej: Cloro Puro)
 * @param rawQuantityUsed Cuánta materia prima se usó (ej: 20L)
 */
public record ProductionBatchRequest(
        Long productId,
        Long bulkProductId,
        BigDecimal rawQuantityUsed
) {
}
