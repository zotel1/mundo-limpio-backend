package com.mundolimpio.application.product.dto;

import java.math.BigDecimal;

public record ProductResponse(Long id, String sku, String name, BigDecimal minPrice, Boolean active)  {
}
