package com.mundolimpio.application.productionbatch.service;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.repository.ProductRepository;
import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchRequest;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchResponse;
import com.mundolimpio.application.inventory.service.InventoryService;
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

    @Mock
    private InventoryService inventoryService;

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

    // ==================== STOCK VALIDATION TESTS (CRIT-2) ====================

    /**
     * Test: createBatch con stock suficiente debe crear el batch y reducir el stock.
     * <p>
     * QUE VERIFICA:
     * - BulkProduct con 100L, rawQuantityUsed 20L → batch creado, stock → 80L.
     * - bulkProductRepository.save() es llamado con el stock actualizado.
     */
    @Test
    void createBatch_withSufficientStock_createsBatch() {
        // Setup
        Long productId = 1L;
        Long bulkProductId = 1L;
        BigDecimal rawQuantityUsed = new BigDecimal("20.00");
        BigDecimal initialStock = new BigDecimal("100.00");
        BigDecimal expectedStock = new BigDecimal("80.00");

        Product product = new Product(productId, "LAVANDINA-001", "Lavandina 3L", new BigDecimal("10.00"), true);
        BulkProduct bulkProduct = new BulkProduct(bulkProductId, "Cloro Puro", initialStock,
                new BigDecimal("5.50"), new BigDecimal("4.0"));

        ProductionBatchRequest request = new ProductionBatchRequest(productId, bulkProductId, rawQuantityUsed);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(bulkProductRepository.findById(bulkProductId)).thenReturn(Optional.of(bulkProduct));

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

        // El stock debe reducirse de 100 a 80
        verify(bulkProductRepository).save(argThat(savedBulkProduct ->
                savedBulkProduct.getCurrentStockLiters().compareTo(expectedStock) == 0
        ));
        verify(inventoryService).incrementStock(productId, new BigDecimal("80.000"));
    }

    /**
     * Test: createBatch con stock insuficiente debe lanzar IllegalArgumentException.
     * <p>
     * QUE VERIFICA:
     * - BulkProduct con 10L, rawQuantityUsed 20L → IAE con mensaje "available 10.0, required 20.0".
     * - NO se llama a bulkProductRepository.save() (el stock no se modifica).
     * - NO se llama a inventoryService.incrementStock().
     */
    @Test
    void createBatch_withInsufficientStock_throwsException() {
        // Setup
        Long productId = 1L;
        Long bulkProductId = 1L;
        BigDecimal rawQuantityUsed = new BigDecimal("20.00");
        BigDecimal initialStock = new BigDecimal("10.00");

        Product product = new Product(productId, "LAVANDINA-001", "Lavandina 3L", new BigDecimal("10.00"), true);
        BulkProduct bulkProduct = new BulkProduct(bulkProductId, "Cloro Puro", initialStock,
                new BigDecimal("5.50"), new BigDecimal("4.0"));

        ProductionBatchRequest request = new ProductionBatchRequest(productId, bulkProductId, rawQuantityUsed);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(bulkProductRepository.findById(bulkProductId)).thenReturn(Optional.of(bulkProduct));

        // Execute & Verify
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createProductionBatch(request));
        assertTrue(ex.getMessage().contains("available 10.0") && ex.getMessage().contains("required 20.0"));

        // NO debe modificar stock ni crear batch ni actualizar inventario
        verify(bulkProductRepository, never()).save(any(BulkProduct.class));
        verify(productionBatchRepository, never()).save(any(ProductionBatch.class));
        verify(inventoryService, never()).incrementStock(anyLong(), any(BigDecimal.class));
    }

    /**
     * Test: createBatch con stock exacto (borde) debe crear el batch y dejar stock en 0.
     * <p>
     * QUE VERIFICA:
     * - BulkProduct con 20L, rawQuantityUsed 20L → batch creado, stock → 0L.
     * - bulkProductRepository.save() es llamado con stock 0.
     */
    @Test
    void createBatch_withExactStock_createsBatch() {
        // Setup
        Long productId = 1L;
        Long bulkProductId = 1L;
        BigDecimal rawQuantityUsed = new BigDecimal("20.00");
        BigDecimal initialStock = new BigDecimal("20.00");
        BigDecimal expectedStock = BigDecimal.ZERO;

        Product product = new Product(productId, "LAVANDINA-001", "Lavandina 3L", new BigDecimal("10.00"), true);
        BulkProduct bulkProduct = new BulkProduct(bulkProductId, "Cloro Puro", initialStock,
                new BigDecimal("5.50"), new BigDecimal("4.0"));

        ProductionBatchRequest request = new ProductionBatchRequest(productId, bulkProductId, rawQuantityUsed);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(bulkProductRepository.findById(bulkProductId)).thenReturn(Optional.of(bulkProduct));

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

        // El stock debe quedar en 0
        verify(bulkProductRepository).save(argThat(savedBulkProduct ->
                savedBulkProduct.getCurrentStockLiters().compareTo(expectedStock) == 0
        ));
        verify(inventoryService).incrementStock(productId, new BigDecimal("80.000"));
    }

    /**
     * Test: createBatch con BulkProduct inactivo debe lanzar IllegalArgumentException.
     * <p>
     * QUE VERIFICA:
     * - BulkProduct con active=false → IAE con mensaje "Bulk product is not active".
     * - NO se llama a bulkProductRepository.save() (stock no modificado).
     * - NO se llama a inventoryService.incrementStock().
     */
    @Test
    void createBatch_withInactiveBulkProduct_throwsException() {
        // Setup
        Long productId = 1L;
        Long bulkProductId = 1L;
        BigDecimal rawQuantityUsed = new BigDecimal("20.00");
        BigDecimal initialStock = new BigDecimal("100.00");

        Product product = new Product(productId, "LAVANDINA-001", "Lavandina 3L", new BigDecimal("10.00"), true);
        BulkProduct bulkProduct = new BulkProduct(bulkProductId, "Cloro Puro", initialStock,
                new BigDecimal("5.50"), new BigDecimal("4.0"));
        bulkProduct.setActive(false); // Inactivo

        ProductionBatchRequest request = new ProductionBatchRequest(productId, bulkProductId, rawQuantityUsed);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(bulkProductRepository.findById(bulkProductId)).thenReturn(Optional.of(bulkProduct));

        // Execute & Verify
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createProductionBatch(request));
        assertTrue(ex.getMessage().contains("Bulk product is not active"));

        // NO debe modificar stock ni crear batch ni actualizar inventario
        verify(bulkProductRepository, never()).save(any(BulkProduct.class));
        verify(productionBatchRepository, never()).save(any(ProductionBatch.class));
        verify(inventoryService, never()).incrementStock(anyLong(), any(BigDecimal.class));
    }

    // ==================== INVENTORY INTEGRATION TESTS ====================

    /**
     * Test: createProductionBatch debe llamar a InventoryService.incrementStock
     * despues de guardar el lote.
     *
     * QUE VERIFICA:
     * - inventoryService.incrementStock() es llamado con el productId y la
     *   cantidad producida (initialQuantity = rawQuantityUsed * conversionRatio).
     * - La llamada ocurre DENTRO del mismo @Transactional, despues del save.
     *
     * POR QUE este test:
     * - Verifica la integracion entre ProductionBatch e Inventory.
     * - Al crear un lote de produccion, el inventory module debe reflejar
     *   el incremento de stock del producto terminado.
     *
     * DIFERENCIA con shouldCreateProductionBatchSuccessfully:
     * - Ese test verifica la logica core de creacion de lotes.
     * - Este test verifica ADEMAS que se integra correctamente con Inventory.
     */
    @Test
    void createProductionBatch_ShouldCallInventoryServiceIncrementStock() {
        // Given: mismos datos que el test de creacion exitosa
        Long productId = 1L;
        Long bulkProductId = 1L;
        BigDecimal rawQuantityUsed = new BigDecimal("20.00");

        Product product = new Product(productId, "LAVANDINA-001", "Lavandina 3L", new BigDecimal("10.00"), true);
        BulkProduct bulkProduct = new BulkProduct(null, "Cloro Puro", new BigDecimal("20.00"),
                new BigDecimal("5.50"), new BigDecimal("4.0"));

        ProductionBatchRequest request = new ProductionBatchRequest(productId, bulkProductId, rawQuantityUsed);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(bulkProductRepository.findById(bulkProductId)).thenReturn(Optional.of(bulkProduct));

        // Mockear el mapper y el save del batch
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

        // When: creamos el lote
        ProductionBatchResponse response = service.createProductionBatch(request);

        // Then: debe llamar a inventoryService.incrementStock con productId e initialQuantity
        // initialQuantity = 20.00 * 4.0 = 80.000 (BigDecimal mantiene la escala)
        verify(inventoryService).incrementStock(productId, new BigDecimal("80.000"));

        // Verificar que las demas llamadas siguen ocurriendo
        verify(productRepository).findById(productId);
        verify(bulkProductRepository).findById(bulkProductId);
        verify(productionBatchRepository).save(any(ProductionBatch.class));
        verify(bulkProductRepository).save(any(BulkProduct.class));

        // Verificar respuesta
        assertNotNull(response);
        assertEquals("Lavandina 3L", response.productName());
    }
}
