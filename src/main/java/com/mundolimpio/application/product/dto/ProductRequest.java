package com.mundolimpio.application.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * ProductRequest DTO para crear productos.
 *
 * Validaciones:
 * - sku: No vacío, patrón estricto (mayúsculas, números, guiones)
 * - name: No vacío
 * - minPrice: Positivo y no nulo
 */
public record ProductRequest(
        @NotBlank(message = "SKU cannot be blank")
        @Pattern(
                regexp = "^[A-Z0-9-]+$",
                message = "SKU must contain only uppercase letters, numbers, and hyphens (e.g., DETERGENTE-500ML-001)"
        )
        String sku,

        @NotBlank(message = "Product name cannot be blank")
        String name,

        @NotNull(message = "Min price cannot be null")
        @Positive(message = "Min price must be greater than zero")
        BigDecimal minPrice
) {}
