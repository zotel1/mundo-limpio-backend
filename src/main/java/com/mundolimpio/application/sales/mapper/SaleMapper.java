package com.mundolimpio.application.sales.mapper;

import com.mundolimpio.application.sales.domain.Sale;
import com.mundolimpio.application.sales.domain.SaleItem;
import com.mundolimpio.application.sales.dto.SaleRequest;
import com.mundolimpio.application.sales.dto.SaleResponse;
import com.mundolimpio.application.sales.dto.SaleItemResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SaleMapper {

    public Sale toEntity(SaleRequest request) {
        // Minimal implementation for GREEN phase
        // Will be enhanced in Phase 3 with full FIFO logic
        return new Sale(request.quantity()); // Temporary: using quantity as totalAmount
    }

    public SaleResponse toResponse(Sale sale) {
        List<SaleItemResponse> itemResponses = sale.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        return new SaleResponse(
                sale.getId(),
                sale.getTotalAmount(),
                sale.getCreatedAt(),
                itemResponses
        );
    }

    private SaleItemResponse toItemResponse(SaleItem item) {
        return new SaleItemResponse(
                item.getProductionBatchId(),
                new java.math.BigDecimal(item.getQuantity()), // Convert int to BigDecimal for now
                item.getUnitPriceAtSale(),
                item.getUnitCostAtSale()
        );
    }
}
