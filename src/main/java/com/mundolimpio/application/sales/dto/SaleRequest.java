package com.mundolimpio.application.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * DTO de entrada para crear una venta.
 * 
 * POR QUÉ un record en vez de una clase:
 * - Los Java Records (Java 16+) son inmutables por defecto: todos los campos
 *   son final y no tienen setters. Esto previene modificaciones accidentales.
 * - Son más concisos: menos boilerplate que una clase con constructor, getters, equals, hashCode.
 * - Ideal para DTOs que solo transportan datos sin comportamiento.
 * 
 * POR QUÉ BigDecimal en vez de Integer/Long para quantity:
 * - Los productos pueden venderse en fracciones (ej: 2.5 litros de detergente).
 * - BigDecimal evita problemas de precisión que tendríamos con double/float.
 * 
 * Validaciones Jakarta:
 * - @NotNull: Spring valida automáticamente antes de llegar al controller.
 *   Si productId o quantity son null, retorna 400 Bad Request sin ejecutar lógica.
 * - @Positive: quantity debe ser mayor a 0. No tiene sentido vender 0 o -5 unidades.
 *   Estas validaciones actúan como primera línea de defensa antes de la lógica de negocio.
 */
public record SaleRequest(
    @NotNull(message = "Product ID cannot be null")
    Long productId,

    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be greater than zero")
    BigDecimal quantity
) {}
