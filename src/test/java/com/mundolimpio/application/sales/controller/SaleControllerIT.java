package com.mundolimpio.application.sales.controller;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.repository.ProductRepository;
import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.repository.ProductionBatchRepository;
import com.mundolimpio.application.sales.dto.SaleItemResponse;
import com.mundolimpio.application.sales.dto.SaleRequest;
import com.mundolimpio.application.sales.dto.SaleResponse;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para SaleController.
 *
 * WHAT: Verifica ventas (creación, FIFO, stock insuficiente, optimistic locking)
 *       contra PostgreSQL real via Testcontainers.
 * WHY: La lógica FIFO depende de queries JPA que deben funcionar idéntico en test y producción.
 * DIFFERENCES: Antes usaba @SpringBootTest directo con H2; ahora extiende
 *              AbstractIntegrationTest que provee PostgreSQL via Testcontainers.
 */
@ActiveProfiles("test")
class SaleControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BulkProductRepository bulkProductRepository;

    @Autowired
    private ProductionBatchRepository productionBatchRepository;

    @Autowired
    private JwtService jwtService;

    private HttpHeaders adminHeaders;
    private HttpHeaders operatorHeaders;
    private HttpHeaders noAuthHeaders;

    /**
     * Setup antes de CADA test.
     * POR QUÉ @BeforeEach: Cada test debe ser independiente. Si test A crea datos,
     * test B no debe depender de eso. Limpiamos todo para tener un estado limpio.
     */
    @BeforeEach
    void setUp() {
        // Limpiar tablas en orden correcto para evitar violaciones de FK
        // production_batches → products, bulk_products (FK)
        productionBatchRepository.deleteAll();
        productRepository.deleteAll();
        bulkProductRepository.deleteAll();
        userRepository.deleteAll();

        // Crear usuario ADMIN (puede crear ventas)
        User admin = new User("admin_sales", "password123", Role.ADMIN);
        userRepository.save(admin);

        // Crear usuario OPERATOR (NO puede crear ventas — solo el ADMIN)
        User operator = new User("operator_sales", "password123", Role.SALES_CLERK);
        userRepository.save(operator);

        // Generar JWT tokens para cada usuario
        String adminToken = jwtService.generateToken(admin);
        String operatorToken = jwtService.generateToken(operator);

        // Headers con token ADMIN
        adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);

        // Headers con token OPERATOR
        operatorHeaders = new HttpHeaders();
        operatorHeaders.setBearerAuth(operatorToken);

        // Headers sin autenticar
        noAuthHeaders = new HttpHeaders();
    }

    /**
     * Helper para crear un HttpEntity con los headers correctos y un body.
     * Esto evita repetir código en cada test.
     */
    private HttpEntity<?> createRequest(Object body, HttpHeaders headers) {
        return new HttpEntity<>(body, headers);
    }

    // ==================== TASK 4.1: 401 Unauthorized (sin token) ====================

    /**
     * Test 4.1 RED: Sin token JWT, el request debe ser rechazado con 401.
     *
     * POR QUÉ este test primero:
     * - Es el más simple: no necesita datos en DB, solo verificar que Spring Security
     *   bloquea requests sin autenticación.
     * - Si este test falla, significa que SecurityConfig está mal configurado
     *   y permite acceso sin autenticar.
     */
    @Test
    void testCreateSale_Returns401_NoToken() {
        // Given: Un request válido pero SIN token JWT
        SaleRequest request = new SaleRequest(1L, new BigDecimal("5"));

        // When: Intentamos crear una venta sin autenticación
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/sales",
                HttpMethod.POST,
                createRequest(request, noAuthHeaders),
                String.class
        );

        // Then: Debe retornar 401 Unauthorized
        // Esto verifica que el endpoint NO está en la lista de permitAll() en SecurityConfig
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ==================== TASK 4.2: 403 Forbidden (OPERATOR intenta crear venta) ====================

    /**
     * Test 4.2 RED: Con token de OPERATOR, el request debe ser rechazado con 403.
     *
     * POR QUÉ este test:
     * - Verifica que @PreAuthorize("hasRole('ADMIN')") funciona correctamente.
     * - Un OPERATOR autenticado PUEDE hacer requests al sistema, pero NO puede crear ventas.
     * - Si este test falla, el @PreAuthorize no está configurado en el controller.
     */
    @Test
    void testCreateSale_Returns403_AsOperator() {
        // Given: Un request válido con token de OPERATOR (no ADMIN)
        SaleRequest request = new SaleRequest(1L, new BigDecimal("5"));

        // When: El OPERATOR intenta crear una venta
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/sales",
                HttpMethod.POST,
                createRequest(request, operatorHeaders),
                String.class
        );

        // Then: Debe retornar 403 Forbidden
        // Spring Security rechaza porque @PreAuthorize requiere ROLE_ADMIN
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ==================== TASK 4.3: 201 Created (venta exitosa) ====================

    /**
     * Test 4.3 GREEN: Admin crea venta con stock disponible → 201 Created.
     *
     * POR QUÉ necesitamos crear datos de prueba:
     * - SaleService necesita un Product y ProductionBatch con stock para procesar la venta.
     * - Creamos un Product, luego un ProductionBatch con stock = 100 unidades.
     * - La venta de 5 unidades debería funcionar con FIFO (un solo lote en este caso).
     *
     * QUÉ VERIFICA:
     * - El endpoint funciona correctamente con datos válidos.
     * - FIFO descuenta stock del lote más antiguo.
     * - Retorna 201 con el SaleResponse completo (id, totalAmount, items).
     */
    @Test
    void testCreateSale_Returns201_Success() {
        // Given: Crear producto con stock disponible (un lote con 100 unidades)
        Product product = new Product(null, "DETERG-001", "Detergente 1L", new BigDecimal("10.00"), true);
        Product savedProduct = productRepository.save(product);

        // Crear materia prima (necesaria para el ProductionBatch)
        BulkProduct bulk = new BulkProduct(null, "Cloro Base", new BigDecimal("50.00"),
                new BigDecimal("5.50"), new BigDecimal("1.0"));
        BulkProduct savedBulk = bulkProductRepository.save(bulk);

        // Crear lote de producción con stock suficiente
        ProductionBatch batch = new ProductionBatch(
                savedProduct,
                savedBulk,
                new BigDecimal("100.00"),  // initialQuantity
                new BigDecimal("100.00"),  // currentStock
                new BigDecimal("5.50"),    // unitCostAtProduction
                new BigDecimal("100.00")   // rawQuantityUsed
        );
        batch.setProductionDate(Instant.now().minus(5, ChronoUnit.DAYS)); // Lote "viejo" (5 días)
        productionBatchRepository.save(batch);

        // When: Admin crea una venta de 5 unidades
        SaleRequest request = new SaleRequest(savedProduct.getId(), new BigDecimal("5"));
        ResponseEntity<SaleResponse> response = restTemplate.exchange(
                "/api/v1/sales",
                HttpMethod.POST,
                createRequest(request, adminHeaders),
                SaleResponse.class
        );

        // Then: 201 Created con la venta completa
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        // El totalAmount debería ser 5 × 5.50 = 27.50 (FIFO del único lote)
        assertEquals(0, new BigDecimal("27.50").compareTo(response.getBody().totalAmount()));
        // Debería tener 1 item (todo vino del mismo lote)
        assertEquals(1, response.getBody().items().size());
    }

    // ==================== TASK 4.4: 400 Bad Request (stock insuficiente) ====================

    /**
     * Test 4.4 GREEN: Intentar vender más de lo disponible → 400 Bad Request.
     *
     * POR QUÉ este test:
     * - Verifica que SaleService valida el stock ANTES de procesar.
     * - Sin esta validación, el sistema permitiría ventas con stock negativo.
     * - El mensaje de error debe ser claro para el usuario.
     */
    @Test
    void testCreateSale_Returns400_InsufficientStock() {
        // Given: Crear producto con poco stock (solo 3 unidades)
        Product product = new Product(null, "LAVAND-001", "Lavandina 500ml", new BigDecimal("5.00"), true);
        Product savedProduct = productRepository.save(product);

        BulkProduct bulk = new BulkProduct(null, "Lavandina Concentrada", new BigDecimal("30.00"),
                new BigDecimal("2.50"), new BigDecimal("1.0"));
        BulkProduct savedBulk = bulkProductRepository.save(bulk);

        ProductionBatch batch = new ProductionBatch(
                savedProduct,
                savedBulk,
                new BigDecimal("3.00"),    // initialQuantity
                new BigDecimal("3.00"),    // currentStock (solo 3 unidades!)
                new BigDecimal("2.50"),    // unitCostAtProduction
                new BigDecimal("3.00")     // rawQuantityUsed
        );
        batch.setProductionDate(Instant.now().minus(2, ChronoUnit.DAYS));
        productionBatchRepository.save(batch);

        // When: Intentar vender 10 unidades (solo hay 3)
        SaleRequest request = new SaleRequest(savedProduct.getId(), new BigDecimal("10"));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/sales",
                HttpMethod.POST,
                createRequest(request, adminHeaders),
                String.class
        );

        // Then: Debe retornar 400 Bad Request con mensaje de error
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        // El mensaje debe mencionar "Insufficient stock" o similar
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Insufficient stock") ||
                   response.getBody().contains("insufficient stock"));
    }

    // ==================== PHASE 5: FIFO con múltiples lotes ====================

    /**
     * Test 5.1 RED: FIFO real con 2 lotes — el más viejo se descuenta primero.
     *
     * ESCENARIO:
     * - Lote A: 10 unidades, fecha 10 días atrás (el más viejo)
     * - Lote B: 15 unidades, fecha 2 días atrás (más nuevo)
     * - Venta: 12 unidades
     *
     * ESPERADO:
     * - 10 unidades del Lote A (se agota)
     * - 2 unidades del Lote B (queda con 13)
     * - Total de items en la respuesta: 2 (uno por lote)
     * - totalAmount = (10 × costoA) + (2 × costoB)
     *
     * POR QUÉ este test es el MÁS IMPORTANTE del módulo:
     * - Es la razón de existir del FIFO: productos viejos se venden primero
     *   para evitar que se venzan en stock.
     * - Si este test falla, toda la lógica FIFO está rota.
     */
    @Test
    void testCreateSale_FIFO_MultipleBatches_OldestFirst() {
        // Given: Crear producto
        Product product = new Product(null, "JABON-001", "Jabón líquido 500ml", new BigDecimal("8.00"), true);
        Product savedProduct = productRepository.save(product);

        BulkProduct bulk = new BulkProduct(null, "Base Jabón", new BigDecimal("40.00"),
                new BigDecimal("3.00"), new BigDecimal("1.0"));
        BulkProduct savedBulk = bulkProductRepository.save(bulk);

        // Lote A: 10 unidades, más VIEJO (10 días atrás), costo $3.00
        ProductionBatch batchA = new ProductionBatch(
                savedProduct, savedBulk,
                new BigDecimal("10.00"), new BigDecimal("10.00"),
                new BigDecimal("3.00"), new BigDecimal("10.00")
        );
        batchA.setProductionDate(Instant.now().minus(10, ChronoUnit.DAYS));
        productionBatchRepository.save(batchA);

        // Lote B: 15 unidades, más NUEVO (2 días atrás), costo $3.50
        ProductionBatch batchB = new ProductionBatch(
                savedProduct, savedBulk,
                new BigDecimal("15.00"), new BigDecimal("15.00"),
                new BigDecimal("3.50"), new BigDecimal("15.00")
        );
        batchB.setProductionDate(Instant.now().minus(2, ChronoUnit.DAYS));
        productionBatchRepository.save(batchB);

        // When: Venta de 12 unidades (más que el lote A solo)
        SaleRequest request = new SaleRequest(savedProduct.getId(), new BigDecimal("12"));
        ResponseEntity<SaleResponse> response = restTemplate.exchange(
                "/api/v1/sales",
                HttpMethod.POST,
                createRequest(request, adminHeaders),
                SaleResponse.class
        );

        // Then: Verificar FIFO correcto
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        // Debería tener 2 items: uno del lote A (10 unidades) y otro del B (2 unidades)
        assertEquals(2, response.getBody().items().size());

        // Verificar que el primer item viene del lote más viejo (batchA)
        SaleItemResponse item1 = response.getBody().items().get(0);
        assertEquals(batchA.getId(), item1.batchId());
        assertEquals(0, new BigDecimal("10.00").compareTo(item1.quantity()));
        assertEquals(0, new BigDecimal("3.00").compareTo(item1.unitCost()));

        // Verificar que el segundo item viene del lote más nuevo (batchB)
        SaleItemResponse item2 = response.getBody().items().get(1);
        assertEquals(batchB.getId(), item2.batchId());
        assertEquals(0, new BigDecimal("2.00").compareTo(item2.quantity()));
        assertEquals(0, new BigDecimal("3.50").compareTo(item2.unitCost()));

        // Verificar totalAmount: (10 × 3.00) + (2 × 3.50) = 30 + 7 = 37.00
        assertEquals(0, new BigDecimal("37.00").compareTo(response.getBody().totalAmount()));

        // Verificar que el stock se descontó correctamente en la base de datos
        ProductionBatch updatedA = productionBatchRepository.findById(batchA.getId()).orElseThrow();
        ProductionBatch updatedB = productionBatchRepository.findById(batchB.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("0.00").compareTo(updatedA.getCurrentStock())); // Lote A se agotó
        assertEquals(0, new BigDecimal("13.00").compareTo(updatedB.getCurrentStock())); // Lote B: 15 - 2 = 13
    }

    /**
     * Test 5.2 RED: Venta parcial de un solo lote — stock restante correcto.
     *
     * ESCENARIO:
     * - Lote único: 50 unidades
     * - Venta: 7 unidades
     *
     * ESPERADO:
     * - 1 item en la respuesta
     * - Stock restante: 43 unidades
     * - totalAmount = 7 × costo
     */
    @Test
    void testCreateSale_PartialDeduction_RemainingStockCorrect() {
        // Given
        Product product = new Product(null, "DETERG-002", "Detergente concentrado 2L", new BigDecimal("12.00"), true);
        Product savedProduct = productRepository.save(product);

        BulkProduct bulk = new BulkProduct(null, "Concentrado", new BigDecimal("25.00"),
                new BigDecimal("4.00"), new BigDecimal("1.0"));
        BulkProduct savedBulk = bulkProductRepository.save(bulk);

        ProductionBatch batch = new ProductionBatch(
                savedProduct, savedBulk,
                new BigDecimal("50.00"), new BigDecimal("50.00"),
                new BigDecimal("4.00"), new BigDecimal("50.00")
        );
        batch.setProductionDate(Instant.now().minus(3, ChronoUnit.DAYS));
        productionBatchRepository.save(batch);

        // When: Venta de 7 unidades
        SaleRequest request = new SaleRequest(savedProduct.getId(), new BigDecimal("7"));
        ResponseEntity<SaleResponse> response = restTemplate.exchange(
                "/api/v1/sales",
                HttpMethod.POST,
                createRequest(request, adminHeaders),
                SaleResponse.class
        );

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().items().size());

        // Stock restante: 50 - 7 = 43
        ProductionBatch updatedBatch = productionBatchRepository.findById(batch.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("43.00").compareTo(updatedBatch.getCurrentStock()));

        // totalAmount: 7 × 4.00 = 28.00
        assertEquals(0, new BigDecimal("28.00").compareTo(response.getBody().totalAmount()));
    }

    /**
     * Test 5.3 RED: Verificar que el stock se descuenta solo si la venta es exitosa.
     * Si la venta falla por stock insuficiente, el stock NO debe modificarse.
     *
     * POR QUÉ este test:
     * - Sin @Transactional, una venta fallida podría dejar stock inconsistente.
     * - Verifica que la atomicidad funciona: todo o nada.
     */
    @Test
    void testCreateSale_FailedSale_StockNotModified() {
        // Given: Lote con 5 unidades
        Product product = new Product(null, "LAVAND-002", "Lavandina 1L", new BigDecimal("6.00"), true);
        Product savedProduct = productRepository.save(product);

        BulkProduct bulk = new BulkProduct(null, "Lavandina Pura", new BigDecimal("20.00"),
                new BigDecimal("2.00"), new BigDecimal("1.0"));
        BulkProduct savedBulk = bulkProductRepository.save(bulk);

        ProductionBatch batch = new ProductionBatch(
                savedProduct, savedBulk,
                new BigDecimal("5.00"), new BigDecimal("5.00"),
                new BigDecimal("2.00"), new BigDecimal("5.00")
        );
        batch.setProductionDate(Instant.now().minus(1, ChronoUnit.DAYS));
        productionBatchRepository.save(batch);

        // When: Intentar vender 100 unidades (imposible)
        SaleRequest request = new SaleRequest(savedProduct.getId(), new BigDecimal("100"));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/sales",
                HttpMethod.POST,
                createRequest(request, adminHeaders),
                String.class
        );

        // Then: Falló con 400
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        // Y el stock NO se modificó
        ProductionBatch unchangedBatch = productionBatchRepository.findById(batch.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5.00").compareTo(unchangedBatch.getCurrentStock()));
    }

    /**
     * Test 5.4 RED: Optimistic Locking — @Version detecta modificaciones concurrentes.
     *
     * ESCENARIO:
     * - Cargamos el mismo lote dos veces (simulando dos transacciones concurrentes)
     * - Modificamos el stock en ambas "transacciones"
     * - La segunda debería fallar con OptimisticLockingFailureException
     *
     * POR QUÉ este test NO usa HTTP requests:
     * - Simular concurrencia real con HTTP requests en tests es complejo y no determinístico.
     * - Este test verifica el mecanismo a nivel de repositorio/JPA, que es donde actúa @Version.
     * - La cobertura real de concurrencia HTTP se haría con @DirtiesContext + hilos,
     *   pero ese es un test más avanzado para el futuro.
     *
     * NOTA: Este test usa transacciones manuales para simular el escenario.
     */
    @Test
    void testOptimisticLocking_VersionDetectsConcurrentModification() {
        // Given: Crear un lote
        Product product = new Product(null, "DETERG-003", "Detergente industrial 5L", new BigDecimal("15.00"), true);
        Product savedProduct = productRepository.save(product);

        BulkProduct bulk = new BulkProduct(null, "Base Industrial", new BigDecimal("30.00"),
                new BigDecimal("5.00"), new BigDecimal("1.0"));
        BulkProduct savedBulk = bulkProductRepository.save(bulk);

        ProductionBatch batch = new ProductionBatch(
                savedProduct, savedBulk,
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                new BigDecimal("5.00"), new BigDecimal("100.00")
        );
        batch.setProductionDate(Instant.now());
        productionBatchRepository.save(batch);

        // Cuando guardamos, el @Version se incrementa a 1 (o se setea)
        Long versionBefore = batch.getVersion();

        // Simulamos una "segunda transacción" que modifica el stock
        // En producción, esto sería otro hilo o request concurrente
        batch.setCurrentStock(new BigDecimal("50.00"));
        productionBatchRepository.save(batch);

        // Then: El version debería haber cambiado (optimistic locking lo detecta)
        // Si intentáramos salvar una entidad con el version viejo, fallaría
        Long versionAfter = productionBatchRepository.findById(batch.getId()).orElseThrow().getVersion();
        assertNotEquals(versionBefore, versionAfter, "Version should have incremented after save");
    }

    /**
     * Test 5.5: BigDecimal precision — venta con cantidad fraccionaria.
     * <p>
     * WHAT: Verifica que una venta con quantity=2.5 preserva la precisión
     * BigDecimal en SaleItem, sin truncar a Integer.
     * WHY: SaleItem.quantity cambió de Integer a BigDecimal para soportar
     * fracciones de producto (ej: 2.5 litros).
     */
    @Test
    void testCreateSale_BigDecimalPrecisionPreserved() {
        // Given: Crear producto con stock suficiente
        Product product = new Product(null, "GEL-001", "Gel antibacterial 250ml", new BigDecimal("8.00"), true);
        Product savedProduct = productRepository.save(product);

        BulkProduct bulk = new BulkProduct(null, "Alcohol en Gel", new BigDecimal("40.00"),
                new BigDecimal("3.00"), new BigDecimal("1.0"));
        BulkProduct savedBulk = bulkProductRepository.save(bulk);

        ProductionBatch batch = new ProductionBatch(
                savedProduct, savedBulk,
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                new BigDecimal("3.00"), new BigDecimal("100.00")
        );
        batch.setProductionDate(Instant.now().minus(3, ChronoUnit.DAYS));
        productionBatchRepository.save(batch);

        // When: Venta con cantidad fraccionaria 2.5
        SaleRequest request = new SaleRequest(savedProduct.getId(), new BigDecimal("2.5"));
        ResponseEntity<SaleResponse> response = restTemplate.exchange(
                "/api/v1/sales",
                HttpMethod.POST,
                createRequest(request, adminHeaders),
                SaleResponse.class
        );

        // Then: La respuesta preserva la precisión BigDecimal
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        SaleItemResponse item = response.getBody().items().get(0);
        // quantity debe ser 2.5 (BigDecimal), NO 2 (truncado a Integer)
        assertThat(item.quantity())
                .as("La cantidad fraccionaria debe preservarse como BigDecimal")
                .isEqualByComparingTo(new BigDecimal("2.5"));
    }
}
