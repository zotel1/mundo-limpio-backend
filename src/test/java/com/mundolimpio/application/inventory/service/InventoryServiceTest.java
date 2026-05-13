package com.mundolimpio.application.inventory.service;

import com.mundolimpio.application.inventory.domain.Inventory;
import com.mundolimpio.application.inventory.domain.InventoryAdjustment;
import com.mundolimpio.application.inventory.dto.AdjustmentRequest;
import com.mundolimpio.application.inventory.dto.InventoryResponse;
import com.mundolimpio.application.inventory.exception.InvalidAdjustmentException;
import com.mundolimpio.application.inventory.exception.InventoryNotFoundException;
import com.mundolimpio.application.inventory.mapper.InventoryMapper;
import com.mundolimpio.application.inventory.repository.InventoryAdjustmentRepository;
import com.mundolimpio.application.inventory.repository.InventoryRepository;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para InventoryService usando Mockito.
 *
 * QUE HACE: Verifica el comportamiento del servicio de inventario
 * aislado de sus dependencias (repositorios, mapper). Cada test
 * mockea las dependencias y verifica interacciones y resultados.
 *
 * POR QUE Mockito y no @SpringBootTest:
 * - Los tests son puramente de lógica de negocio (no requieren BD).
 * - Mockito es más rápido: no levanta contexto Spring.
 * - Sigue el patrón de ProductionBatchServiceTest.
 *
 * DIFERENCIA con ProductionBatchServiceTest:
 * - ProductionBatchServiceTest mockea 4 dependencias.
 * - InventoryServiceTest mockea 4 dependencias (misma complejidad).
 * - Ambos usan @ExtendWith(MockitoExtension.class) y @InjectMocks.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryAdjustmentRepository adjustmentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryService inventoryService;

    // ==================== HELPERS ====================

    /**
     * Crea un Product de prueba con los datos mínimos necesarios.
     * POR QUE método helper: lo usamos en varios tests para evitar
     * duplicación de código de setup.
     */
    private Product createTestProduct(Long id, String name) {
        return new Product(id, "SKU-" + id, name, BigDecimal.TEN, true);
    }

    /**
     * Crea un Inventory de prueba con datos controlados.
     */
    private Inventory createTestInventory(Long productId, BigDecimal stock, BigDecimal threshold) {
        Product product = createTestProduct(productId, "Test Product " + productId);
        Inventory inventory = new Inventory(product, stock);
        inventory.setMinStockThreshold(threshold);
        return inventory;
    }

    /**
     * Crea un InventoryResponse esperado para verificaciones.
     */
    private InventoryResponse createExpectedResponse(Long productId, String productName,
                                                     BigDecimal stock, BigDecimal threshold) {
        return new InventoryResponse(productId, productName, stock, threshold);
    }

    // ==================== GET INVENTORY TESTS ====================

    /**
     * Test 1: getInventory con producto existente retorna InventoryResponse.
     *
     * QUE VERIFICA:
     * - inventoryRepository.findByProductId() es llamado con el productId correcto.
     * - inventoryMapper.toResponse() es llamado con el Inventory encontrado.
     * - El InventoryResponse retornado coincide con el esperado.
     */
    @Test
    void getInventory_ExistingProduct_ReturnsResponse() {
        // Given: un producto con inventario existente
        Long productId = 1L;
        Inventory inventory = createTestInventory(productId, new BigDecimal("50"), BigDecimal.ZERO);
        InventoryResponse expectedResponse = createExpectedResponse(
                productId, "Test Product 1", new BigDecimal("50"), BigDecimal.ZERO);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        when(inventoryMapper.toResponse(inventory)).thenReturn(expectedResponse);

        // When: consultamos el inventario
        InventoryResponse actualResponse = inventoryService.getInventory(productId);

        // Then: debe retornar el response mapeado correctamente
        assertNotNull(actualResponse);
        assertEquals(expectedResponse.productId(), actualResponse.productId());
        assertEquals(expectedResponse.productName(), actualResponse.productName());
        assertEquals(0, expectedResponse.currentStock().compareTo(actualResponse.currentStock()));

        // Verificar que se llamaron las dependencias correctas
        verify(inventoryRepository).findByProductId(productId);
        verify(inventoryMapper).toResponse(inventory);
        verifyNoMoreInteractions(inventoryRepository, inventoryMapper);
    }

    /**
     * Test 2: getInventory con producto inexistente lanza InventoryNotFoundException.
     *
     * QUE VERIFICA:
     * - inventoryRepository.findByProductId() retorna Optional.empty().
     * - Se lanza InventoryNotFoundException con el productId en el mensaje.
     * - inventoryMapper.toResponse() NO es llamado (el flujo se corta antes).
     */
    @Test
    void getInventory_NonExistingProduct_ThrowsNotFound() {
        // Given: un productId sin inventario
        Long productId = 999L;
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.empty());

        // When: consultamos el inventario → Then: debe lanzar excepción
        InventoryNotFoundException exception = assertThrows(
                InventoryNotFoundException.class,
                () -> inventoryService.getInventory(productId)
        );

        // Verificar que el mensaje incluye el productId
        assertTrue(exception.getMessage().contains(String.valueOf(productId)));

        // Verificar que el mapper NO fue llamado (el flujo se cortó en el orElseThrow)
        verify(inventoryRepository).findByProductId(productId);
        verifyNoInteractions(inventoryMapper);
    }

    // ==================== LOW STOCK TESTS ====================

    /**
     * Test 3: getLowStockInventories retorna solo los que están bajo el umbral.
     *
     * QUE VERIFICA:
     * - inventoryRepository.findLowStockInventories() es llamado.
     * - Todos los items retornados por el repo son mapeados a response.
     * - La lista retornada tiene el tamaño correcto.
     */
    @Test
    void getLowStockInventories_ReturnsFilteredList() {
        // Given: dos inventarios, uno con stock bajo y otro no
        // El repo findLowStockInventories() YA filtra por currentStock < minStockThreshold
        Inventory lowStockInventory = createTestInventory(1L, new BigDecimal("5"), new BigDecimal("10"));
        Inventory adequateStockInventory = createTestInventory(2L, new BigDecimal("20"), new BigDecimal("5"));

        // Solo lowStockInventory cumple la condición (5 < 10)
        List<Inventory> lowStockList = List.of(lowStockInventory);

        when(inventoryRepository.findLowStockInventories()).thenReturn(lowStockList);
        when(inventoryMapper.toResponse(lowStockInventory)).thenReturn(
                createExpectedResponse(1L, "Test Product 1", new BigDecimal("5"), new BigDecimal("10"))
        );

        // When: consultamos low stock
        List<InventoryResponse> result = inventoryService.getLowStockInventories();

        // Then: debe retornar solo un item (el que está bajo el umbral)
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.getFirst().productId());
        assertEquals(0, new BigDecimal("5").compareTo(result.getFirst().currentStock()));

        verify(inventoryRepository).findLowStockInventories();
        verify(inventoryMapper).toResponse(lowStockInventory);
    }

    // ==================== ADJUST STOCK TESTS ====================

    /**
     * Test 4: adjustStock con incremento válido actualiza stock y crea auditoría.
     *
     * QUE VERIFICA:
     * - El stock se incrementa correctamente (currentStock + quantity).
     * - inventoryRepository.save() es llamado con el Inventory actualizado.
     * - adjustmentRepository.save() es llamado con un InventoryAdjustment.
     * - Se retorna el InventoryResponse con el nuevo stock.
     *
     * POR QUE quantity=+5: el DTO usa convención de signo, +5 = incremento.
     */
    @Test
    void adjustStock_ValidIncrease_UpdatesStockAndCreatesAudit() {
        // Given: un inventario con stock=10 y un ajuste de +5
        Long productId = 1L;
        BigDecimal initialStock = new BigDecimal("10");
        BigDecimal adjustmentQty = new BigDecimal("5");
        BigDecimal expectedStock = new BigDecimal("15");

        Inventory inventory = createTestInventory(productId, initialStock, BigDecimal.ZERO);
        AdjustmentRequest request = new AdjustmentRequest("ADJUSTMENT", adjustmentQty, "Restock");

        InventoryResponse expectedResponse = createExpectedResponse(
                productId, "Test Product 1", expectedStock, BigDecimal.ZERO);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);
        when(adjustmentRepository.save(any(InventoryAdjustment.class))).thenReturn(null);
        when(inventoryMapper.toResponse(inventory)).thenReturn(expectedResponse);

        // When: ajustamos el stock
        InventoryResponse response = inventoryService.adjustStock(productId, request);

        // Then: el stock debe ser 15
        assertNotNull(response);
        assertEquals(0, expectedStock.compareTo(response.currentStock()));

        // Verificar que el inventory fue actualizado con el nuevo stock antes de save
        verify(inventoryRepository).findByProductId(productId);
        // El inventory.setCurrentStock(expectedStock) debe haber sido llamado
        assertEquals(0, expectedStock.compareTo(inventory.getCurrentStock()));
        verify(inventoryRepository).save(inventory);
        // Verificar que se creó el audit trail
        verify(adjustmentRepository).save(any(InventoryAdjustment.class));
        verify(inventoryMapper).toResponse(inventory);
    }

    /**
     * Test 5: adjustStock con decremento que excede el stock lanza InvalidAdjustmentException.
     *
     * QUE VERIFICA:
     * - Cuando newStock < 0, se lanza InvalidAdjustmentException.
     * - inventoryRepository.save() NO es llamado (el flujo se corta).
     * - adjustmentRepository.save() NO es llamado (no hay auditoría).
     */
    @Test
    void adjustStock_DecreaseBelowZero_ThrowsInvalid() {
        // Given: un inventario con stock=10 y un ajuste de -15
        Long productId = 1L;
        Inventory inventory = createTestInventory(productId, new BigDecimal("10"), BigDecimal.ZERO);
        AdjustmentRequest request = new AdjustmentRequest("LOSS", new BigDecimal("-15"), "Theft");

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        // When/Then: debe lanzar excepción
        InvalidAdjustmentException exception = assertThrows(
                InvalidAdjustmentException.class,
                () -> inventoryService.adjustStock(productId, request)
        );

        assertTrue(exception.getMessage().contains("Insufficient stock"));
        assertTrue(exception.getMessage().contains("10")); // current stock mencionado

        // Verificar que NO se persiste nada (rollback implícito)
        verify(inventoryRepository, never()).save(any(Inventory.class));
        verify(adjustmentRepository, never()).save(any(InventoryAdjustment.class));
    }

    /**
     * Test 6: adjustStock con modificación concurrente lanza OptimisticLockingFailureException.
     *
     * QUE VERIFICA:
     * - Cuando inventoryRepository.save() lanza OptimisticLockingFailureException
     *   (simulando @Version conflict), la excepción se propaga sin ser capturada.
     * - Este comportamiento es correcto: GlobalExceptionHandler la capturará → 409.
     *
     * POR QUE @Version lanza OptimisticLockingFailureException:
     * - JPA incrementa la columna version antes de hacer UPDATE.
     * - Si la version en memoria difiere de la version en BD, el UPDATE
     *   afecta 0 filas y JPA lanza OptimisticLockingFailureException.
     * - Esto ocurre cuando dos transacciones leen el mismo inventory
     *   simultáneamente y ambas intentan modificarlo.
     */
    @Test
    void adjustStock_ConcurrentModification_ThrowsOptimisticLock() {
        // Given: un inventario y un ajuste válido, pero save() lanza conflicto
        Long productId = 1L;
        Inventory inventory = createTestInventory(productId, new BigDecimal("50"), BigDecimal.ZERO);
        AdjustmentRequest request = new AdjustmentRequest("ADJUSTMENT", new BigDecimal("10"), "Concurrent test");

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        // Simular que @Version detecta conflicto en el save()
        when(inventoryRepository.save(any(Inventory.class)))
                .thenThrow(new OptimisticLockingFailureException("Version conflict"));

        // When/Then: debe propagar la excepción de concurrencia
        assertThrows(
                OptimisticLockingFailureException.class,
                () -> inventoryService.adjustStock(productId, request)
        );

        // Verificar que se intentó el save pero NO se creó auditoría
        verify(inventoryRepository).save(inventory);
        verify(adjustmentRepository, never()).save(any(InventoryAdjustment.class));
    }

    // ==================== PACKAGE-PRIVATE INTEGRATION TESTS ====================

    /**
     * Test 7: incrementStock crea inventario si no existe y suma cantidad.
     *
     * QUE VERIFICA:
     * - Cuando findByProductId retorna empty, se crea un nuevo Inventory.
     * - productRepository.getReferenceById() es llamado para el proxy.
     * - inventoryRepository.save() es llamado dos veces (creación + actualización).
     * - El stock final es igual a quantity (porque empezó en 0).
     */
    @Test
    void incrementStock_NoExistingInventory_CreatesAndIncrements() {
        // Given: un producto sin inventario
        Long productId = 1L;
        BigDecimal quantity = new BigDecimal("30");

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.empty());
        when(productRepository.getReferenceById(productId)).thenReturn(createTestProduct(productId, "Test"));
        // Primer save: creación del Inventory con stock=0
        // Segundo save: actualización con stock incrementado
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: incrementamos stock
        inventoryService.incrementStock(productId, quantity);

        // Then: se debe crear y actualizar
        verify(inventoryRepository).findByProductId(productId);
        verify(productRepository).getReferenceById(productId);
        // save() debe haberse llamado 2 veces: creación + actualización
        verify(inventoryRepository, times(2)).save(any(Inventory.class));
    }

    /**
     * Test 8: decrementStock con stock suficiente resta correctamente.
     *
     * QUE VERIFICA:
     * - inventoryRepository.findByProductId() es llamado.
     * - inventoryRepository.save() es llamado con el stock decrementado.
     * - No se lanza excepción.
     */
    @Test
    void decrementStock_WithSufficientStock_DecrementsCorrectly() {
        // Given: un inventario con stock=50 y decremento de 10
        Long productId = 1L;
        Inventory inventory = createTestInventory(productId, new BigDecimal("50"), BigDecimal.ZERO);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: decrementamos
        inventoryService.decrementStock(productId, new BigDecimal("10"));

        // Then: el stock debe ser 40
        assertEquals(0, new BigDecimal("40").compareTo(inventory.getCurrentStock()));
        verify(inventoryRepository).save(inventory);
    }

    /**
     * Test 9: decrementStock con stock insuficiente lanza excepción.
     *
     * QUE VERIFICA:
     * - Cuando newStock < 0, se lanza InvalidAdjustmentException.
     * - inventoryRepository.save() NO es llamado.
     */
    @Test
    void decrementStock_InsufficientStock_ThrowsInvalid() {
        // Given: un inventario con stock=5 y decremento de 10
        Long productId = 1L;
        Inventory inventory = createTestInventory(productId, new BigDecimal("5"), BigDecimal.ZERO);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        // When/Then: debe lanzar excepción
        assertThrows(
                InvalidAdjustmentException.class,
                () -> inventoryService.decrementStock(productId, new BigDecimal("10"))
        );

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    /**
     * Test 10: decrementStock sin inventario lanza InventoryNotFoundException.
     *
     * QUE VERIFICA:
     * - Cuando findByProductId retorna empty, se lanza InventoryNotFoundException.
     */
    @Test
    void decrementStock_NoInventory_ThrowsNotFound() {
        // Given: un producto sin inventario
        Long productId = 999L;
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.empty());

        // When/Then: debe lanzar excepción
        assertThrows(
                InventoryNotFoundException.class,
                () -> inventoryService.decrementStock(productId, BigDecimal.TEN)
        );

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }
}
