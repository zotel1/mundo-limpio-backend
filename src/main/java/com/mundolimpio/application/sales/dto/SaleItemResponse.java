package com.mundolimpio.application.sales.dto;

import java.math.BigDecimal;

/**
 * DTO de salida que representa un item individual de una venta.
 * 
 * POR QUÉ estos campos:
 * - batchId: Identifica de qué lote se descontó stock (para trazabilidad).
 * - quantity: Cuántas unidades se tomaron de este lote en particular.
 *   Nota: Una venta con 10 unidades puede generar 2 items si viene de 2 lotes.
 * - unitPrice: Precio unitario aplicado (snapshot del costo del lote en el momento de la venta).
 * - unitCost: Costo unitario del lote (para cálculo de márgenes de ganancia).
 * 
 * POR QUÉ BigDecimal en quantity: Para mantener consistencia con SaleRequest
 * y poder representar fracciones (ej: 0.5 litros).
 */
public record SaleItemResponse(
    Long batchId,            // ID del lote de producción del que se descontó stock
    BigDecimal quantity,     // Cantidad descontada de este lote específico
    BigDecimal unitPrice,    // Precio unitario en el momento de la venta
    BigDecimal unitCost      // Costo unitario del lote (para cálculo de margen)
) {}
