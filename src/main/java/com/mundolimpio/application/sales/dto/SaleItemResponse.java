package com.mundolimpio.application.sales.dto;

import java.math.BigDecimal;

/**
 * DTO de salida que representa un item individual de una venta.
 * 
 * POR QUÉ estos campos:
 * - batchId: Identifica de qué lote se descontó stock (para trazabilidad).
 * - quantity: Cuántas unidades se tomaron de este lote en particular.
 *   Nota: Una venta con 10 unidades puede generar 2 items si viene de 2 lotes.
 * - unitPrice: Precio unitario aplicado (snapshot en el momento de la venta).
 * - unitCost: Costo unitario del lote (para cálculo de márgenes de ganancia).
 * - productId: ID del producto vendido (resuelto desde el lote).
 * - productName: Nombre del producto vendido (resuelto desde el lote).
 * 
 * POR QUÉ productId y productName:
 * HIGH-1 — El listado de ventas necesita mostrar qué producto se vendió.
 * Se resuelven desde ProductionBatch → Product en SaleMapper.
 * 
 * DIFFERENCES con PR 1:
 * - productId y productName son nuevos campos para mostrar en GET /sales.
 * - Se resuelven via ProductionBatchRepository en SaleMapper (N+1 aceptable para MVP).
 * 
 * POR QUÉ BigDecimal en quantity: Para mantener consistencia con SaleRequest
 * y poder representar fracciones (ej: 0.5 litros).
 */
public record SaleItemResponse(
    Long batchId,            // ID del lote de producción del que se descontó stock
    BigDecimal quantity,     // Cantidad descontada de este lote específico
    BigDecimal unitPrice,    // Precio unitario en el momento de la venta
    BigDecimal unitCost,     // Costo unitario del lote (para cálculo de margen)
    Long productId,          // WHAT: ID del producto vendido
                             // WHY: HIGH-1 — GET /sales necesita mostrar qué producto
    String productName       // WHAT: Nombre del producto vendido
                             // WHY: HIGH-1 — mostrar nombre legible en la respuesta
) {}
