package com.mundolimpio.application.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SaleRequest(
    @NotNull(message = "Product ID cannot be null")
    Long productId,

    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be greater than zero")
    BigDecimal quantity
) {}
