package com.mundolimpio.application.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de salida que representa una venta completa después de ser creada.
 * 
 * POR QUÉ un record:
 * - Inmutabilidad: una vez creado el response, nadie lo modifica.
 * - Serialización automática: Spring convierte este record a JSON sin configuración extra.
 * - Los campos reflejan exactamente lo que el cliente necesita ver:
 *   id (para referencia futura), totalAmount (cuánto pagó), createdAt (cuándo),
 *   items (detalle de qué lotes se descontaron y en qué cantidades).
 */
public record SaleResponse(
    Long id,                          // ID de la venta creada (para consultas futuras)
    BigDecimal totalAmount,           // Monto total calculado vía FIFO
    LocalDateTime createdAt,          // Fecha/hora de creación
    List<SaleItemResponse> items      // Detalle de items (lotes descontados)
) {}
