package com.mundolimpio.application.bulkproduct.controller;

import com.mundolimpio.application.bulkproduct.dto.BulkProductRequest;
import com.mundolimpio.application.bulkproduct.dto.BulkProductResponse;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de Integración para BulkProductController con Soft Delete.
 *
 * WHAT: Verifica el CRUD + soft delete + reactivate de materias primas via HTTP
 *       contra PostgreSQL real (Testcontainers).
 * WHY: El comportamiento debe ser idéntico al de producción. El soft delete
 *      preserva integridad referencial con production_batches y purchase_items.
 * DIFFERENCES: Antes verificaba hard delete (existsById == false).
 *              Ahora verifica soft delete (active == false, fila persiste).
 */
@ActiveProfiles("test")
class BulkProductControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BulkProductRepository bulkProductRepository;

    @Autowired
    private UserRepository  userRepository;

    @Autowired
    private JwtService jwtService;

    private HttpHeaders adminHeaders;

    @BeforeEach
    void setUp() {
        bulkProductRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User("admin_test", "admin_test@mundolimpio.com", "$2a$10$encodedPassword", Role.ADMIN);
        userRepository.save(admin);

        String token = jwtService.generateToken(admin);
        adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(token);
    }

    private HttpEntity<?> getRequestEntity(Object body) {
        return new HttpEntity<>(body, adminHeaders);
    }

    @Test
    void shouldCreateBulkProductSuccessfully() {
        BulkProductRequest request = new BulkProductRequest(
                "Cloro Puro",
                new BigDecimal("20.00"),
                new BigDecimal("5.50"),
                new BigDecimal("4.0")
        );

        ResponseEntity<BulkProductResponse> response = restTemplate.exchange(
                "/api/v1/bulk-products",
                HttpMethod.POST,
                getRequestEntity(request),
                BulkProductResponse.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        assertEquals("Cloro Puro", response.getBody().name());
        assertEquals(new BigDecimal("4.0"), response.getBody().conversionRatio());
        // Verificar que se crea activo por defecto
        assertTrue(response.getBody().active());
    }

    @Test
    void shouldGetAllBulkProducts() {
        // Crear 2 activos + 1 inactivo
        BulkProductRequest req = new BulkProductRequest(
                "Cloro Puro", new BigDecimal("20.00"), new BigDecimal("5.50"), new BigDecimal("4.0")
        );
        ResponseEntity<BulkProductResponse> r1 = restTemplate.exchange(
                "/api/v1/bulk-products", HttpMethod.POST, getRequestEntity(req), BulkProductResponse.class
        );
        Long id1 = r1.getBody().id();

        BulkProductRequest req2 = new BulkProductRequest(
                "Detergente Base", new BigDecimal("20.00"), new BigDecimal("8.00"), new BigDecimal("3.0")
        );
        restTemplate.exchange("/api/v1/bulk-products", HttpMethod.POST, getRequestEntity(req2), BulkProductResponse.class);

        // Soft-delete el primero para hacerlo inactivo
        restTemplate.exchange("/api/v1/bulk-products/" + id1, HttpMethod.DELETE, getRequestEntity(null), Void.class);

        // GET default debe retornar solo activos (1)
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/v1/bulk-products",
                HttpMethod.GET,
                getRequestEntity(null),
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldGetAllBulkProductsIncludingInactive() {
        // Crear 2 activos + 1 inactivo
        BulkProductRequest req = new BulkProductRequest(
                "Cloro Puro", new BigDecimal("20.00"), new BigDecimal("5.50"), new BigDecimal("4.0")
        );
        ResponseEntity<BulkProductResponse> r1 = restTemplate.exchange(
                "/api/v1/bulk-products", HttpMethod.POST, getRequestEntity(req), BulkProductResponse.class
        );
        Long id1 = r1.getBody().id();

        BulkProductRequest req2 = new BulkProductRequest(
                "Detergente Base", new BigDecimal("20.00"), new BigDecimal("8.00"), new BigDecimal("3.0")
        );
        restTemplate.exchange("/api/v1/bulk-products", HttpMethod.POST, getRequestEntity(req2), BulkProductResponse.class);

        BulkProductRequest req3 = new BulkProductRequest(
                "Desodorante Base", new BigDecimal("10.00"), new BigDecimal("12.00"), new BigDecimal("80.0")
        );
        restTemplate.exchange("/api/v1/bulk-products", HttpMethod.POST, getRequestEntity(req3), BulkProductResponse.class);

        // Soft-delete el primero para hacerlo inactivo
        restTemplate.exchange("/api/v1/bulk-products/" + id1, HttpMethod.DELETE, getRequestEntity(null), Void.class);

        // GET /all debe retornar todos (3)
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/v1/bulk-products/all",
                HttpMethod.GET,
                getRequestEntity(null),
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
    }

    @Test
    void shouldGetBulkProductById() {
        BulkProductRequest request = new BulkProductRequest(
                "Desodorante Base", new BigDecimal("10.00"), new BigDecimal("12.00"), new BigDecimal("80.0")
        );

        ResponseEntity<BulkProductResponse> createResponse = restTemplate.exchange(
                "/api/v1/bulk-products", HttpMethod.POST, getRequestEntity(request), BulkProductResponse.class
        );

        Long id = createResponse.getBody().id();

        ResponseEntity<BulkProductResponse> response = restTemplate.exchange(
                "/api/v1/bulk-products/" + id, HttpMethod.GET, getRequestEntity(null), BulkProductResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Desodorante Base", response.getBody().name());
        assertEquals(0, new BigDecimal("80.00").compareTo(response.getBody().conversionRatio()));
    }

    @Test
    void shouldReturnNotFoundForNonExistentId() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/products/999999", String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldUpdateBulkProduct() {
        BulkProductRequest request = new BulkProductRequest(
                "Jabón Base", new BigDecimal("160.00"), new BigDecimal("15.00"), new BigDecimal("1.0")
        );

        ResponseEntity<BulkProductResponse> createResponse = restTemplate.exchange(
                "/api/v1/bulk-products", HttpMethod.POST, getRequestEntity(request), BulkProductResponse.class
        );
        Long id = createResponse.getBody().id();

        BulkProductRequest updateRequest = new BulkProductRequest(
                "Jabón Actualizado", new BigDecimal("200.00"), new BigDecimal("18.00"), new BigDecimal("1.0")
        );

        ResponseEntity<BulkProductResponse> response = restTemplate.exchange(
                "/api/v1/bulk-products/" + id, HttpMethod.PUT, getRequestEntity(updateRequest), BulkProductResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Jabón Actualizado", response.getBody().name());
        assertEquals(new BigDecimal("200.00"), response.getBody().currentStockLiters());
    }

    @Test
    void shouldDeleteBulkProduct() {
        BulkProductRequest request = new BulkProductRequest(
                "Para borrar", new BigDecimal("50.00"), new BigDecimal("10.00"), new BigDecimal("1.0")
        );

        ResponseEntity<BulkProductResponse> createResponse = restTemplate.exchange(
                "/api/v1/bulk-products", HttpMethod.POST, getRequestEntity(request), BulkProductResponse.class
        );

        Long id = createResponse.getBody().id();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/bulk-products/" + id, HttpMethod.DELETE, getRequestEntity(null), Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verificar que la fila todavía existe (soft delete) pero active=false
        ResponseEntity<BulkProductResponse> getResponse = restTemplate.exchange(
                "/api/v1/bulk-products/" + id, HttpMethod.GET, getRequestEntity(null), BulkProductResponse.class
        );
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());
        assertFalse(getResponse.getBody().active());
    }

    @Test
    void shouldReactivateBulkProduct() {
        BulkProductRequest request = new BulkProductRequest(
                "Para reactivar", new BigDecimal("30.00"), new BigDecimal("7.00"), new BigDecimal("2.0")
        );

        ResponseEntity<BulkProductResponse> createResponse = restTemplate.exchange(
                "/api/v1/bulk-products", HttpMethod.POST, getRequestEntity(request), BulkProductResponse.class
        );
        Long id = createResponse.getBody().id();

        // Soft-delete primero
        restTemplate.exchange("/api/v1/bulk-products/" + id, HttpMethod.DELETE, getRequestEntity(null), Void.class);

        // Reactivar
        ResponseEntity<Void> reactivateResponse = restTemplate.exchange(
                "/api/v1/bulk-products/" + id + "/reactivate",
                HttpMethod.PATCH,
                getRequestEntity(null),
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, reactivateResponse.getStatusCode());

        // Verificar que ahora está activo
        ResponseEntity<BulkProductResponse> getResponse = restTemplate.exchange(
                "/api/v1/bulk-products/" + id, HttpMethod.GET, getRequestEntity(null), BulkProductResponse.class
        );
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertTrue(getResponse.getBody().active());
    }

    @Test
    void shouldReturn404OnDeleteNonexistent() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/bulk-products/999999",
                HttpMethod.DELETE,
                getRequestEntity(null),
                Void.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturn404OnReactivateNonexistent() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/bulk-products/999999/reactivate",
                HttpMethod.PATCH,
                getRequestEntity(null),
                Void.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
