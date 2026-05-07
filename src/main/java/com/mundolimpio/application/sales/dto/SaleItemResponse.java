package com.mundolimpio.application.sales.dto;

import java.math.BigDecimal;

public record SaleItemResponse(
    Long batchId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal unitCost
) {}
