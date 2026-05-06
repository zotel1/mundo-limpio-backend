package com.mundolimpio.application.productionbatch.mapper;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchRequest;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mapper para convertir entre ProductionBatch (entidad) y DTOs.
 */
@Component
public class ProductionBatchMapper {

    /**
     * Convierte un ProductionBatchRequest a una entidad nueva.
     * Los campos calculados (initialQuantity, unitCost, etc.) se setean en el Service.
     */
    public ProductionBatch toEntity(ProductionBatchRequest request, Product product, BulkProduct bulkProduct,
                                   BigDecimal initialQuantity, BigDecimal currentStock,
                                   BigDecimal unitCostAtProduction) {
        return new ProductionBatch(
                product,
                bulkProduct,
                initialQuantity,
                currentStock,
                unitCostAtProduction,
                request.rawQuantityUsed()
        );
    }

    /**
     * Convierte una entidad ProductionBatch a un ProductionBatchResponse.
     */
    public ProductionBatchResponse toResponse(ProductionBatch entity) {
        return new ProductionBatchResponse(
                entity.getId(),
                entity.getProduct().getId(),
                entity.getProduct().getName(),
                entity.getBulkProduct().getId(),
                entity.getBulkProduct().getName(),
                entity.getInitialQuantity(),
                entity.getCurrentStock(),
                entity.getUnitCostAtProduction(),
                entity.getRawQuantityUsed(),
                entity.getProductionDate()
        );
    }
}
