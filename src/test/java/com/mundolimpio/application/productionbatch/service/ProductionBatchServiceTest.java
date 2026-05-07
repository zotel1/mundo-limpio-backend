package com.mundolimpio.application.productionbatch.service;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.repository.ProductRepository;
import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchRequest;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchResponse;
import com.mundolimpio.application.productionbatch.exception.ProductionBatchNotFoundException;
import com.mundolimpio.application.productionbatch.mapper.ProductionBatchMapper;
import com.mundolimpio.application.productionbatch.repository.ProductionBatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test for ProductionBatchService.
 * Uses Mockito to mock dependencies (no DB, no full app startup).
 */
@ExtendWith(MockitoExtension.class)
class ProductionBatchServiceTest {

    @Mock
    private ProductionBatchRepository productionBatchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BulkProductRepository bulkProductRepository;

    @Mock
    private ProductionBatchMapper productionBatchMapper;

    @InjectMocks
    private ProductionBatchService service;

    @Test
    void shouldCreateProductionBatchSuccessfully() {
        // Setup
        Long productId = 1L;
        Long bulkProductId = 1L;
        BigDecimal rawQuantityUsed = new BigDecimal("20.00");

        Product product = new Product(productId, "LAVANDINA-001", "Lavandina 3L", new BigDecimal("10.00"), true);
        BulkProduct bulkProduct = new BulkProduct(null, "Cloro Puro", new BigDecimal("20.00"),
                new BigDecimal("5.50"), new BigDecimal("4.0"));

        ProductionBatchRequest request = new ProductionBatchRequest(productId, bulkProductId, rawQuantityUsed);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(bulkProductRepository.findById(bulkProductId)).thenReturn(Optional.of(bulkProduct));

        // The mapper returns a new batch
        ProductionBatch mockBatch = mock(ProductionBatch.class);
        when(productionBatchMapper.toEntity(any(ProductionBatchRequest.class), any(Product.class),
                any(BulkProduct.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(mockBatch);

        ProductionBatch savedBatch = new ProductionBatch(product, bulkProduct,
                new BigDecimal("80.00"), new BigDecimal("80.00"),
                new BigDecimal("1.375"), rawQuantityUsed);
        when(productionBatchRepository.save(any(ProductionBatch.class))).thenReturn(savedBatch);

        ProductionBatchResponse mockResponse = new ProductionBatchResponse(
                1L, productId, "Lavandina 3L", bulkProductId, "Cloro Puro",
                new BigDecimal("80.00"), new BigDecimal("80.00"),
                new BigDecimal("1.375"), rawQuantityUsed, null
        );
        when(productionBatchMapper.toResponse(any(ProductionBatch.class))).thenReturn(mockResponse);

        // Execute
        ProductionBatchResponse response = service.createProductionBatch(request);

        // Verify
        assertNotNull(response);
        assertEquals("Lavandina 3L", response.productName());
        // 20L raw * 4 (ratio) = 80L produced
        assertEquals(0, new BigDecimal("80.00").compareTo(response.initialQuantity()));

        verify(productRepository).findById(productId);
        verify(bulkProductRepository).findById(bulkProductId);
        verify(productionBatchRepository).save(any(ProductionBatch.class));
        verify(bulkProductRepository).save(any(BulkProduct.class)); // Stock should be updated
    }

    @Test
    void shouldThrowExceptionWhenProductNotFound() {
        ProductionBatchRequest request = new ProductionBatchRequest(999L, 1L, BigDecimal.TEN);
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ProductionBatchNotFoundException.class, () -> service.createProductionBatch(request));
    }

    @Test
    void shouldThrowExceptionWhenBulkProductNotFound() {
        Long productId = 1L;
        ProductionBatchRequest request = new ProductionBatchRequest(productId, 999L, BigDecimal.TEN);

        when(productRepository.findById(productId)).thenReturn(Optional.of(mock(Product.class)));
        when(bulkProductRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ProductionBatchNotFoundException.class, () -> service.createProductionBatch(request));
    }
}
