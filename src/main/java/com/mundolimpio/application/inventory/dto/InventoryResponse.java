package com.mundolimpio.application.inventory.dto;

import java.math.BigDecimal;

/**
 * DTO de respuesta para consultas de inventario.
 *
 * QUE HACE: Expone los datos de inventario de un producto: su ID, nombre,
 * stock actual y umbral mínimo. No expone el ID interno de Inventory ni
 * el version (son detalles de implementación).
 *
 * POR QUE: Usamos un record de Java por las siguientes razones:
 *   1. Inmutabilidad: una vez creado, el response no puede modificarse.
 *      Esto es importante porque los DTOs viajan a través de la red y
 *      no deberían cambiar después de ser enviados.
 *   2. Boilerplate reducido: un record genera automáticamente constructor,
 *      getters, equals(), hashCode() y toString(). No necesitamos escribir
 *      ni mantener nada de eso.
 *   3. Consistencia: sigue el mismo patrón que ProductResponse, que también
 *      es un record.
 *
 * DIFERENCIA con ProductResponse:
 *   - ProductResponse incluye id, sku, name, minPrice, active (datos de catálogo).
 *   - InventoryResponse incluye productId (FK), productName (denormalizado
 *     para evitar N+1 en la capa de presentación), currentStock y
 *     minStockThreshold (datos operacionales de stock).
 */
public record InventoryResponse(
        Long productId,
        String productName,
        BigDecimal currentStock,
        BigDecimal minStockThreshold
) {}
