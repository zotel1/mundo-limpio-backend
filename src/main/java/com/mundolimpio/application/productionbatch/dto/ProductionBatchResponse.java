package com.mundolimpio.application.productionbatch.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Record para la respuesta de un lote de producción.
 *
 * @param id ID del lote
 * @param productId ID del producto terminado
 * @param productName Nombre del producto (para mostrar)
 * @param bulkProductId ID de la materia prima
 * @param bulkProductName Nombre de la materia prima
 * @param initialQuantity Cantidad inicial producida
 * @param currentStock Stock actual disponible
 * @param unitCostAtProduction Costo unitario al producir
 * @param rawQuantityUsed Materia prima usada
 * @param productionDate Fecha de producción
 */
public record ProductionBatchResponse(
        Long id,
        Long productId,
        String productName,
        Long bulkProductId,
        String bulkProductName,
        BigDecimal initialQuantity,
        BigDecimal currentStock,
        BigDecimal unitCostAtProduction,
        BigDecimal rawQuantityUsed,
        Instant productionDate
) {
}
