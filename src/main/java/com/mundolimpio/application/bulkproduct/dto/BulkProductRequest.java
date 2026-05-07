package com.mundolimpio.application.bulkproduct.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Record para la peticion de creacion/actualizacion de materia prima.
 *
 * @param name Nombre del producto (ej: "Cloro Puro", "Detergente Base")
 * @param currentStockLiters Stock actual en litros
 * @param costperLiter Costo por litro de materia prima
 * @param conversionRatio Ratio de conversion (ej: 4 para cloro 1:4)
 */
public record BulkProductRequest(
        String name,
        BigDecimal currentStockLiters,
        BigDecimal costperLiter,
        BigDecimal conversionRatio
) {}
