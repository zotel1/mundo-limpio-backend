package com.mundolimpio.application.product.service;

import com.mundolimpio.application.common.exception.ProductAlreadyExistsException;
import com.mundolimpio.application.common.exception.ProductNotFoundException;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.dto.ProductRequest;
import com.mundolimpio.application.product.dto.ProductResponse;
import com.mundolimpio.application.product.mapper.ProductMapper;
import com.mundolimpio.application.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para ProductService usando Mockito.
 *
 * QUE HACE: Verifica el comportamiento del servicio de productos
 * aislado de sus dependencias (ProductRepository, ProductMapper).
 * Cada test mockea las dependencias y verifica interacciones y resultados.
 *
 * POR QUÉ Mockito y no @SpringBootTest:
 * - Los tests son puramente de lógica de negocio (no requieren BD).
 * - Mockito es más rápido: no levanta contexto Spring.
 * - Sigue el patrón de InventoryServiceTest.
 *
 * DIFFERENCES con InventoryServiceTest:
 * - InventoryServiceTest mockea 4 dependencias.
 * - ProductServiceTest mockea 2 dependencias (ProductRepository, ProductMapper).
 * - Ambos usan @ExtendWith(MockitoExtension.class) y @InjectMocks.
 * - InventoryServiceTest tiene 10 tests; ProductServiceTest tiene 17 tests
 *   (cubre más operaciones: create, read×4, update, soft delete, reactivate).
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    // ==================== HELPERS ====================

    /**
     * Crea un Product de prueba con datos controlados.
     * POR QUÉ método helper: lo usamos en múltiples tests para evitar
     * duplicación de código de setup.
     */
    private Product createTestProduct(Long id, String sku, String name, BigDecimal price, boolean active) {
        return new Product(id, sku, name, price, active);
    }

    /**
     * Crea un ProductRequest de prueba con datos controlados.
     */
    private ProductRequest createTestRequest(String sku, String name, BigDecimal price) {
        return new ProductRequest(sku, name, price);
    }

    /**
     * Crea un ProductResponse esperado para verificaciones.
     */
    private ProductResponse createExpectedResponse(Long id, String sku, String name,
                                                    BigDecimal price, boolean active) {
        return new ProductResponse(id, sku, name, price, active);
    }

    // ==================== CREATE TESTS ====================

    /**
     * Test 1: createProduct exitoso retorna ProductResponse con los datos guardados.
     *
     * QUE VERIFICA:
     * - existsBySku() retorna false (SKU disponible).
     * - toEntity() convierte el request a entidad.
     * - save() persiste la entidad y retorna la versión con ID.
     * - toResponse() convierte la entidad guardada a DTO.
     * - El response tiene los campos correctos.
     */
    @Test
    void createProduct_Success_ReturnsResponse() {
        // Given: un request con SKU nuevo
        ProductRequest request = createTestRequest("SKU-001", "Test Product", BigDecimal.TEN);
        Product product = createTestProduct(null, "SKU-001", "Test Product", BigDecimal.TEN, true);
        Product savedProduct = createTestProduct(1L, "SKU-001", "Test Product", BigDecimal.TEN, true);
        ProductResponse expectedResponse = createExpectedResponse(1L, "SKU-001", "Test Product",
                BigDecimal.TEN, true);

        when(productRepository.existsBySku("SKU-001")).thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(product)).thenReturn(savedProduct);
        when(productMapper.toResponse(savedProduct)).thenReturn(expectedResponse);

        // When: creamos el producto
        ProductResponse result = productService.createProduct(request);

        // Then: debe retornar el response mapeado correctamente
        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("SKU-001", result.sku());
        assertEquals("Test Product", result.name());
        assertEquals(0, BigDecimal.TEN.compareTo(result.minPrice()));
        assertTrue(result.active());

        // Verificar que se llamaron las dependencias en el orden correcto
        verify(productRepository).existsBySku("SKU-001");
        verify(productMapper).toEntity(request);
        verify(productRepository).save(product);
        verify(productMapper).toResponse(savedProduct);
        verifyNoMoreInteractions(productRepository, productMapper);
    }

    /**
     * Test 2: createProduct con SKU duplicado lanza ProductAlreadyExistsException.
     *
     * QUE VERIFICA:
     * - existsBySku() retorna true → se lanza excepción.
     * - toEntity() y save() NO son llamados (el flujo se corta antes).
     */
    @Test
    void createProduct_DuplicateSku_ThrowsAlreadyExists() {
        // Given: un request con SKU que ya existe
        ProductRequest request = createTestRequest("SKU-DUP", "Duplicate", BigDecimal.TEN);
        when(productRepository.existsBySku("SKU-DUP")).thenReturn(true);

        // When/Then: debe lanzar excepción
        ProductAlreadyExistsException exception = assertThrows(
                ProductAlreadyExistsException.class,
                () -> productService.createProduct(request)
        );

        // Verificar que el mensaje incluye el SKU conflictivo
        assertTrue(exception.getMessage().contains("SKU-DUP"));

        // El flujo se corta en existsBySku, nada más se ejecuta
        verify(productRepository).existsBySku("SKU-DUP");
        verify(productRepository, never()).save(any());
        verifyNoInteractions(productMapper);
    }

    /**
     * Test 3: createProduct con error de validación en el flujo de guardado propaga la excepción.
     *
     * QUE VERIFICA:
     * - Si el repositorio lanza RuntimeException al guardar, la excepción se propaga.
     * - El servicio no captura ni envuelve excepciones de infraestructura.
     *
     * POR QUÉ "validation error": simula un DataIntegrityViolationException
     * que podría ocurrir si hay constraints violados en la BD.
     */
    @Test
    void createProduct_ValidationError_Throws() {
        // Given: SKU disponible pero el save falla
        ProductRequest request = createTestRequest("SKU-VAL", "Validation", BigDecimal.TEN);
        Product product = createTestProduct(null, "SKU-VAL", "Validation", BigDecimal.TEN, true);

        when(productRepository.existsBySku("SKU-VAL")).thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(product))
                .thenThrow(new RuntimeException("Constraint violation"));

        // When/Then: la excepción se propaga
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> productService.createProduct(request)
        );

        assertTrue(exception.getMessage().contains("Constraint violation"));

        // Verificar que se intentó guardar pero el mapper toResponse nunca se llamó
        verify(productRepository).existsBySku("SKU-VAL");
        verify(productMapper).toEntity(request);
        verify(productRepository).save(product);
        verify(productMapper, never()).toResponse(any());
    }

    // ==================== GET BY ID TESTS ====================

    /**
     * Test 4: getProductById con ID existente retorna ProductResponse.
     *
     * QUE VERIFICA:
     * - findById() retorna el producto → toResponse() lo convierte.
     * - El response tiene los datos correctos.
     */
    @Test
    void getProductById_ExistingId_ReturnsResponse() {
        // Given: un producto con ID 1
        Long id = 1L;
        Product product = createTestProduct(id, "SKU-BYID", "By ID", BigDecimal.ONE, true);
        ProductResponse expectedResponse = createExpectedResponse(id, "SKU-BYID", "By ID", BigDecimal.ONE, true);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(expectedResponse);

        // When: consultamos por ID
        ProductResponse result = productService.getProductById(id);

        // Then: debe retornar el response correcto
        assertNotNull(result);
        assertEquals(id, result.id());
        assertEquals("SKU-BYID", result.sku());

        verify(productRepository).findById(id);
        verify(productMapper).toResponse(product);
    }

    /**
     * Test 5: getProductById con ID inexistente lanza ProductNotFoundException.
     *
     * QUE VERIFICA:
     * - findById() retorna Optional.empty() → se lanza excepción.
     * - toResponse() NO es llamado (el flujo se corta).
     */
    @Test
    void getProductById_NonExistentId_ThrowsNotFound() {
        // Given: un ID sin producto
        Long id = 999L;
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then: debe lanzar excepción
        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> productService.getProductById(id)
        );

        // Verificar que el mensaje incluye el ID
        assertTrue(exception.getMessage().contains("999"));

        verify(productRepository).findById(id);
        verifyNoInteractions(productMapper);
    }

    // ==================== GET BY SKU TESTS ====================

    /**
     * Test 6: getProductBySku con SKU existente retorna ProductResponse.
     *
     * QUE VERIFICA:
     * - findBySku() retorna el producto → toResponse() lo convierte.
     */
    @Test
    void getProductBySku_ExistingSku_ReturnsResponse() {
        // Given: un producto con SKU conocido
        String sku = "SKU-FOUND";
        Product product = createTestProduct(1L, sku, "Found", BigDecimal.TEN, true);
        ProductResponse expectedResponse = createExpectedResponse(1L, sku, "Found", BigDecimal.TEN, true);

        when(productRepository.findBySku(sku)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(expectedResponse);

        // When: consultamos por SKU
        ProductResponse result = productService.getProductBySku(sku);

        // Then: debe retornar el response correcto
        assertNotNull(result);
        assertEquals(sku, result.sku());
        assertEquals("Found", result.name());

        verify(productRepository).findBySku(sku);
        verify(productMapper).toResponse(product);
    }

    /**
     * Test 7: getProductBySku con SKU inexistente lanza ProductNotFoundException.
     *
     * QUE VERIFICA:
     * - findBySku() retorna Optional.empty() → se lanza excepción.
     * - toResponse() NO es llamado.
     */
    @Test
    void getProductBySku_NonExistentSku_ThrowsNotFound() {
        // Given: un SKU que no existe
        String sku = "SKU-404";
        when(productRepository.findBySku(sku)).thenReturn(Optional.empty());

        // When/Then: debe lanzar excepción
        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> productService.getProductBySku(sku)
        );

        // Verificar que el mensaje incluye el SKU
        assertTrue(exception.getMessage().contains("SKU-404"));

        verify(productRepository).findBySku(sku);
        verifyNoInteractions(productMapper);
    }

    // ==================== GET ALL TESTS ====================

    /**
     * Test 8: getAllActiveProducts retorna solo productos con active=true.
     *
     * QUE VERIFICA:
     * - findByActiveTrue() es llamado (no findAll).
     * - Todos los productos retornados son mapeados a response.
     * - La lista tiene el tamaño correcto.
     */
    @Test
    void getAllActiveProducts_ReturnsOnlyActiveProducts() {
        // Given: dos productos activos
        Product p1 = createTestProduct(1L, "SKU-A1", "Active 1", BigDecimal.TEN, true);
        Product p2 = createTestProduct(2L, "SKU-A2", "Active 2", BigDecimal.ONE, true);
        ProductResponse r1 = createExpectedResponse(1L, "SKU-A1", "Active 1", BigDecimal.TEN, true);
        ProductResponse r2 = createExpectedResponse(2L, "SKU-A2", "Active 2", BigDecimal.ONE, true);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> productPage = new PageImpl<>(List.of(p1, p2), pageable, 2);

        when(productRepository.findByActiveTrue(any(Pageable.class))).thenReturn(productPage);
        when(productMapper.toResponse(p1)).thenReturn(r1);
        when(productMapper.toResponse(p2)).thenReturn(r2);

        // When: consultamos productos activos
        Page<ProductResponse> result = productService.getAllActiveProducts(pageable);

        // Then: debe retornar solo los activos
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertTrue(result.getContent().get(0).active());
        assertTrue(result.getContent().get(1).active());

        // Verificar que se usó findByActiveTrue con Pageable (no findAll)
        verify(productRepository).findByActiveTrue(any(Pageable.class));
        verify(productMapper).toResponse(p1);
        verify(productMapper).toResponse(p2);
    }

    /**
     * Test 9: getAllProducts retorna todos los productos (activos e inactivos).
     *
     * QUE VERIFICA:
     * - findAll() es llamado (no findByActiveTrue).
     * - La lista incluye productos con active=false.
     */
    @Test
    void getAllProducts_ReturnsAllProductsIncludingInactive() {
        // Given: un producto activo y uno inactivo
        Product active = createTestProduct(1L, "SKU-ACT", "Active", BigDecimal.TEN, true);
        Product inactive = createTestProduct(2L, "SKU-INACT", "Inactive", BigDecimal.ONE, false);
        ProductResponse rActive = createExpectedResponse(1L, "SKU-ACT", "Active", BigDecimal.TEN, true);
        ProductResponse rInactive = createExpectedResponse(2L, "SKU-INACT", "Inactive", BigDecimal.ONE, false);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> productPage = new PageImpl<>(List.of(active, inactive), pageable, 2);

        when(productRepository.findAll(any(Pageable.class))).thenReturn(productPage);
        when(productMapper.toResponse(active)).thenReturn(rActive);
        when(productMapper.toResponse(inactive)).thenReturn(rInactive);

        // When: consultamos todos los productos
        Page<ProductResponse> result = productService.getAllProducts(pageable);

        // Then: debe incluir ambos
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertTrue(result.getContent().get(0).active());
        assertFalse(result.getContent().get(1).active());

        // Verificar que se usó findAll con Pageable
        verify(productRepository).findAll(any(Pageable.class));
        verify(productMapper, times(2)).toResponse(any());
    }

    // ==================== UPDATE TESTS ====================

    /**
     * Test 10: updateProduct exitoso actualiza los campos y retorna el response.
     *
     * QUE VERIFICA:
     * - findById() encuentra el producto existente.
     * - existsBySku() confirma que el nuevo SKU no está en uso.
     * - Los setters del producto son llamados con los nuevos valores.
     * - save() persiste los cambios.
     * - El response tiene los datos actualizados.
     */
    @Test
    void updateProduct_Success_ReturnsUpdatedResponse() {
        // Given: un producto existente con SKU antiguo
        Long id = 1L;
        ProductRequest request = createTestRequest("SKU-NEW", "Updated Name", new BigDecimal("20.00"));
        Product existing = createTestProduct(id, "SKU-OLD", "Old Name", BigDecimal.TEN, true);
        ProductResponse expectedResponse = createExpectedResponse(id, "SKU-NEW", "Updated Name",
                new BigDecimal("20.00"), true);

        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySku("SKU-NEW")).thenReturn(false);
        when(productRepository.save(existing)).thenReturn(existing);
        when(productMapper.toResponse(existing)).thenReturn(expectedResponse);

        // When: actualizamos el producto
        ProductResponse result = productService.updateProduct(id, request);

        // Then: debe retornar los datos actualizados
        assertNotNull(result);
        assertEquals("SKU-NEW", result.sku());
        assertEquals("Updated Name", result.name());
        assertEquals(0, new BigDecimal("20.00").compareTo(result.minPrice()));

        // Verificar que los setters del entity fueron invocados
        assertEquals("SKU-NEW", existing.getSku());
        assertEquals("Updated Name", existing.getName());
        assertEquals(0, new BigDecimal("20.00").compareTo(existing.getMinPrice()));

        verify(productRepository).findById(id);
        verify(productRepository).existsBySku("SKU-NEW");
        verify(productRepository).save(existing);
        verify(productMapper).toResponse(existing);
    }

    /**
     * Test 11: updateProduct con ID inexistente lanza ProductNotFoundException.
     *
     * QUE VERIFICA:
     * - findById() retorna Optional.empty() → se lanza excepción.
     * - save() NO es llamado.
     */
    @Test
    void updateProduct_NonExistentId_ThrowsNotFound() {
        // Given: un ID que no existe
        Long id = 999L;
        ProductRequest request = createTestRequest("SKU", "Name", BigDecimal.TEN);
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then: debe lanzar excepción
        assertThrows(
                ProductNotFoundException.class,
                () -> productService.updateProduct(id, request)
        );

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any());
        verifyNoInteractions(productMapper);
    }

    /**
     * Test 12: updateProduct con SKU conflictivo lanza ProductAlreadyExistsException.
     *
     * QUE VERIFICA:
     * - El producto existe pero el nuevo SKU ya está en uso por otro producto.
     * - save() NO es llamado (el flujo se corta antes de persistir).
     */
    @Test
    void updateProduct_SkuConflict_ThrowsAlreadyExists() {
        // Given: un producto existente y un request con SKU que ya está en uso
        Long id = 1L;
        ProductRequest request = createTestRequest("SKU-CONFLICT", "Conflict", BigDecimal.TEN);
        Product existing = createTestProduct(id, "SKU-OLD", "Old", BigDecimal.TEN, true);

        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySku("SKU-CONFLICT")).thenReturn(true);

        // When/Then: debe lanzar excepción de conflicto
        assertThrows(
                ProductAlreadyExistsException.class,
                () -> productService.updateProduct(id, request)
        );

        // Los setters NO deben haberse llamado (el flujo se corta en existsBySku)
        verify(productRepository).findById(id);
        verify(productRepository).existsBySku("SKU-CONFLICT");
        verify(productRepository, never()).save(any());
        verifyNoInteractions(productMapper);
    }

    // ==================== SOFT DELETE TESTS ====================

    /**
     * Test 13: deleteProductSoftDelete exitoso marca active=false y persiste.
     *
     * QUE VERIFICA:
     * - El producto se marca como inactivo (active=false).
     * - save() es llamado para persistir el cambio.
     * - No se lanza excepción.
     */
    @Test
    void deleteProductSoftDelete_Success_MarksInactive() {
        // Given: un producto activo
        Long id = 1L;
        Product product = createTestProduct(id, "SKU-DEL", "To Delete", BigDecimal.TEN, true);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        // When: realizamos soft delete
        productService.deleteProductSoftDelete(id);

        // Then: el producto debe estar inactivo
        assertFalse(product.getActive());

        verify(productRepository).findById(id);
        verify(productRepository).save(product);
    }

    /**
     * Test 14: deleteProductSoftDelete con ID inexistente lanza ProductNotFoundException.
     *
     * QUE VERIFICA:
     * - findById() retorna Optional.empty() → se lanza excepción.
     * - save() NO es llamado.
     */
    @Test
    void deleteProductSoftDelete_NonExistentId_ThrowsNotFound() {
        // Given: un ID sin producto
        Long id = 999L;
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then: debe lanzar excepción
        assertThrows(
                ProductNotFoundException.class,
                () -> productService.deleteProductSoftDelete(id)
        );

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any());
    }

    // ==================== REACTIVATE TESTS ====================

    /**
     * Test 15: reactivateProduct exitoso marca active=true y persiste.
     *
     * QUE VERIFICA:
     * - Un producto inactivo se marca como activo.
     * - save() es llamado para persistir el cambio.
     */
    @Test
    void reactivateProduct_Success_MarksActive() {
        // Given: un producto inactivo
        Long id = 1L;
        Product product = createTestProduct(id, "SKU-REACT", "To Reactivate", BigDecimal.TEN, false);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        // When: reactivamos
        productService.reactivateProduct(id);

        // Then: el producto debe estar activo
        assertTrue(product.getActive());

        verify(productRepository).findById(id);
        verify(productRepository).save(product);
    }

    /**
     * Test 16: reactivateProduct sobre un producto ya activo es idempotente.
     *
     * QUE VERIFICA:
     * - Llamar reactivate sobre un producto ya activo no lanza error.
     * - save() es llamado igualmente (el servicio no distingue).
     * - El producto sigue activo.
     *
     * POR QUÉ es importante: garantiza que la operación es segura de llamar
     * múltiples veces sin efectos secundarios negativos.
     */
    @Test
    void reactivateProduct_AlreadyActive_Idempotent() {
        // Given: un producto que ya está activo
        Long id = 1L;
        Product product = createTestProduct(id, "SKU-ACTIVE", "Already Active", BigDecimal.TEN, true);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        // When: reactivamos (aunque ya está activo)
        // Then: no debe lanzar excepción — la operación es idempotente
        assertDoesNotThrow(() -> productService.reactivateProduct(id));

        // El producto sigue activo
        assertTrue(product.getActive());

        verify(productRepository).findById(id);
        verify(productRepository).save(product);
    }

    /**
     * Test 17: reactivateProduct con ID inexistente lanza ProductNotFoundException.
     *
     * QUE VERIFICA:
     * - findById() retorna Optional.empty() → se lanza excepción.
     * - save() NO es llamado.
     */
    @Test
    void reactivateProduct_NonExistentId_ThrowsNotFound() {
        // Given: un ID sin producto
        Long id = 999L;
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then: debe lanzar excepción
        assertThrows(
                ProductNotFoundException.class,
                () -> productService.reactivateProduct(id)
        );

        verify(productRepository).findById(id);
        verify(productRepository, never()).save(any());
    }
}
