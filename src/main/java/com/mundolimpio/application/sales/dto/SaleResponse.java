package com.mundolimpio.application.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SaleResponse(
    Long id,
    BigDecimal totalAmount,
    LocalDateTime createdAt,
    List<SaleItemResponse> items
) {}
