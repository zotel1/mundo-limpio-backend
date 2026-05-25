package com.mundolimpio.application.bulkproduct.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Record para la peticion de creacion/actualizacion de materia prima.
 * <p>
 * WHAT: Incluye validaciones Jakarta para todos los campos.
 * WHY: Validar datos de entrada antes de procesarlos evita errores
 * de negocio y produce HTTP 400 con mensajes descriptivos.
 * DIFFERENCES: Antes no tenia validaciones en los campos numericos.
 * Ahora name es @NotBlank, currentStockLiters @PositiveOrZero,
 * costPerLiter y conversionRatio @Positive.
 *
 * @param name Nombre del producto (ej: "Cloro Puro", "Detergente Base")
 * @param currentStockLiters Stock actual en litros
 * @param costPerLiter Costo por litro de materia prima
 * @param conversionRatio Ratio de conversion (ej: 4 para cloro 1:4)
 */
public record BulkProductRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero BigDecimal currentStockLiters,
        @NotNull @Positive BigDecimal costPerLiter,
        @NotNull @Positive BigDecimal conversionRatio
) {}
