package com.mundolimpio.application.productionbatch.controller;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.repository.ProductRepository;
import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchRequest;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchResponse;
import com.mundolimpio.application.productionbatch.repository.ProductionBatchRepository;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.application.security.service.JwtService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de Integración (E2E) para ProductionBatchController.
 *
 * WHAT: Verifica creación y consulta de lotes de producción via HTTP contra PostgreSQL real.
 * WHY: Usar la misma DB que producción garantiza compatibilidad total.
 * DIFFERENCES: Antes usaba @SpringBootTest directo con H2; ahora extiende
 *              AbstractIntegrationTest que provee PostgreSQL via Testcontainers.
 */
@ActiveProfiles("test")
class ProductionBatchControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BulkProductRepository bulkProductRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductionBatchRepository productionBatchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        // Limpiar tablas en orden para evitar violaciones de FK
        // production_batches tiene FK a products y bulk_products
        productionBatchRepository.deleteAll();
        productRepository.deleteAll();
        bulkProductRepository.deleteAll();
        userRepository.deleteAll();

        // Crear usuario ADMIN
        User admin = new User("admin_prod", "admin_prod@mundolimpio.com", "password", Role.ADMIN);
        userRepository.save(admin);

        // Generar JWT token
        String token = jwtService.generateToken(admin);
        adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(token);
    }

    private HttpEntity<?> getRequestEntity(Object body) {
        return new HttpEntity<>(body, adminHeaders);
    }

    @Test
    void shouldCreateProductionBatchSuccessfully() {
        // Crear materia prima
        BulkProduct bulk = new BulkProduct(null, "Cloro Puro", new BigDecimal("20.00"),
                new BigDecimal("5.50"), new BigDecimal("4.0"));
        BulkProduct savedBulk = bulkProductRepository.save(bulk);

        // Crear producto terminado
        Product product = new Product(null, "LAVANDINA-001", "Lavandina 3L", new BigDecimal("10.00"), true);
        Product savedProduct = productRepository.save(product);

        // Crear lote
        ProductionBatchRequest request = new ProductionBatchRequest(
                savedProduct.getId(),
                savedBulk.getId(),
                new BigDecimal("20.00") // 20L de cloro puro
        );

        ResponseEntity<ProductionBatchResponse> response = restTemplate.exchange(
                "/api/v1/production-batches",
                HttpMethod.POST,
                getRequestEntity(request),
                ProductionBatchResponse.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        // 20L * 4 (ratio) = 80L producidos
        assertEquals(0, new BigDecimal("80.0").compareTo(response.getBody().initialQuantity()));
        assertEquals("Lavandina 3L", response.getBody().productName());
    }

    @Test
    void shouldGetBatchesByProductId() {
        // Setup: crear materia prima y producto
        BulkProduct bulk = new BulkProduct(null, "Cloro Puro", new BigDecimal("20.00"),
                new BigDecimal("5.50"), new BigDecimal("4.0"));
        BulkProduct savedBulk = bulkProductRepository.save(bulk);

        Product product = new Product(null, "LAVANDINA-001", "Lavandina 3L", new BigDecimal("10.00"), true);
        Product savedProduct = productRepository.save(product);

        // Crear 2 lotes
        ProductionBatchRequest request1 = new ProductionBatchRequest(
                savedProduct.getId(), savedBulk.getId(), new BigDecimal("10.00"));
        ProductionBatchRequest request2 = new ProductionBatchRequest(
                savedProduct.getId(), savedBulk.getId(), new BigDecimal("15.00"));

        restTemplate.exchange("/api/v1/production-batches", HttpMethod.POST,
                getRequestEntity(request1), ProductionBatchResponse.class);
        restTemplate.exchange("/api/v1/production-batches", HttpMethod.POST,
                getRequestEntity(request2), ProductionBatchResponse.class);

        // Obtener lotes
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/v1/production-batches/product/" + savedProduct.getId(),
                HttpMethod.GET,
                getRequestEntity(null),
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldReturnNotFoundForNonExistentBatch() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/production-batches/999999",
                HttpMethod.GET,
                getRequestEntity(null),
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
