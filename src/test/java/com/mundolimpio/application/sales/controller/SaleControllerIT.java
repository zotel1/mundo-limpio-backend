package com.mundolimpio.application.sales.controller;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.repository.ProductRepository;
import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.repository.ProductionBatchRepository;
import com.mundolimpio.application.sales.dto.SaleRequest;
import com.mundolimpio.application.sales.dto.SaleResponse;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para SaleController.
 *
 * POR QUÉ integration test y no unit test:
 * - Necesitamos verificar que toda la cadena funciona: HTTP → Controller → Service → Repository → DB.
 * - Un unit test con mocks solo verificaría la lógica aislada.
 * - Un integration test con H2 verifica que Spring Security, validaciones y JPA funcionan juntos.
 *
 * CÓMO FUNCIONA:
 * - @SpringBootTest(webEnvironment = RANDOM_PORT): Levanta una instancia real del servidor en un puerto aleatorio.
 * - TestRestTemplate: Cliente HTTP real que hace requests al servidor, como un browser.
 * - Perfil "test": Usa H2 en memoria en vez de PostgreSQL.
 *
 * TESTS que escribimos (RED → GREEN → REFACTOR):
 * - 401: Sin token → Spring Security rechaza antes de llegar al controller
 * - 403: Con token pero role OPERATOR → @PreAuthorize("hasRole('ADMIN')") rechaza
 * - 201: Admin crea venta con stock disponible → FIFO funciona
 * - 400: Stock insuficiente → IllegalArgumentException
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SaleControllerIT {

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
        User operator = new User("operator_sales", "password123", Role.OPERATOR);
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
}
