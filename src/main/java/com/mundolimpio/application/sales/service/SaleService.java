package com.mundolimpio.application.sales.service;

import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.repository.ProductionBatchRepository;
import com.mundolimpio.application.sales.domain.Sale;
import com.mundolimpio.application.sales.domain.SaleItem;
import com.mundolimpio.application.sales.dto.SaleRequest;
import com.mundolimpio.application.sales.dto.SaleResponse;
import com.mundolimpio.application.sales.mapper.SaleMapper;
import com.mundolimpio.application.sales.repository.SaleItemRepository;
import com.mundolimpio.application.sales.repository.SaleRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductionBatchRepository productionBatchRepository;
    private final SaleMapper saleMapper;

    public SaleService(SaleRepository saleRepository,
                       SaleItemRepository saleItemRepository,
                       ProductionBatchRepository productionBatchRepository,
                       SaleMapper saleMapper) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.saleMapper = saleMapper;
    }

    @Transactional
    public SaleResponse createSale(SaleRequest request) {
        try {
            log.info("Creating sale for productId: {}, quantity: {}", request.productId(), request.quantity());

            // 1. Get batches with available stock in FIFO order (oldest first)
            List<ProductionBatch> batches = productionBatchRepository
                    .findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc(
                            request.productId(), BigDecimal.ZERO);

            // 2. Calculate total available stock
            BigDecimal totalAvailable = batches.stream()
                    .map(ProductionBatch::getCurrentStock)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.debug("Total available stock: {}, requested: {}", totalAvailable, request.quantity());

            // 3. Validate stock availability
            if (totalAvailable.compareTo(request.quantity()) < 0) {
                throw new IllegalArgumentException(
                        String.format("Insufficient stock. Available: %.2f, Requested: %.2f",
                                totalAvailable, request.quantity()));
            }

            // 4. Create Sale entity
            Sale sale = new Sale(calculateTotalAmount(batches, request.quantity()));
            sale = saleRepository.save(sale);

            // 5. Apply FIFO deduction
            BigDecimal remainingQuantity = request.quantity();
            List<SaleItem> saleItems = new ArrayList<>();

            for (ProductionBatch batch : batches) {
                if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal batchStock = batch.getCurrentStock();
                BigDecimal quantityFromBatch = remainingQuantity.min(batchStock);

                // Create SaleItem
                SaleItem item = new SaleItem(
                        batch.getId(),
                        quantityFromBatch.intValue(),
                        batch.getUnitCostAtProduction(),  // unit price at sale (using cost as price for now)
                        batch.getUnitCostAtProduction()   // unit cost at sale
                );
                item.setSale(sale);
                sale.addItem(item);
                saleItemRepository.save(item);

                // Update batch stock
                batch.setCurrentStock(batchStock.subtract(quantityFromBatch));
                remainingQuantity = remainingQuantity.subtract(quantityFromBatch);

                log.debug("Deducted {} from batch {}, remaining to deduct: {}",
                        quantityFromBatch, batch.getId(), remainingQuantity);
            }

            // 6. Save and return response
            Sale savedSale = saleRepository.save(sale);
            log.info("Sale created successfully with ID: {}", savedSale.getId());

            return saleMapper.toResponse(savedSale);
        } catch (OptimisticLockingFailureException e) {
            log.error("Optimistic lock error during sale creation: {}", e.getMessage());
            throw new RuntimeException("Concurrent sale conflict. Please retry.", e);
        }
    }

    private BigDecimal calculateTotalAmount(List<ProductionBatch> batches, BigDecimal quantity) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal remaining = quantity;

        for (ProductionBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal batchStock = batch.getCurrentStock();
            BigDecimal quantityFromBatch = remaining.min(batchStock);
            BigDecimal itemTotal = quantityFromBatch.multiply(batch.getUnitCostAtProduction());
            total = total.add(itemTotal);
            remaining = remaining.subtract(quantityFromBatch);
        }

        return total;
    }
}
