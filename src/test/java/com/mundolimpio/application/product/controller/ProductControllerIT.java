package com.mundolimpio.application.product.controller;

import com.mundolimpio.application.product.dto.ProductRequest;
import com.mundolimpio.application.product.dto.ProductResponse;
import com.mundolimpio.application.product.repository.ProductRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de Integración para ProductController con CRUD Completo.
 *
 * WHAT: Verifica el CRUD completo de productos via HTTP contra PostgreSQL real (Testcontainers).
 * WHY: El comportamiento debe ser idéntico al de producción porque usamos la misma DB.
 * DIFFERENCES: Antes usaba @SpringBootTest directo con H2; ahora extiende
 *              AbstractIntegrationTest que provee PostgreSQL via Testcontainers.
 */
class ProductControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Record interno para agrupar el contexto de un ADMIN autenticado.
     *
     * @param id    ID del usuario ADMIN en DB
     * @param token JWT generado para ese ADMIN
     */
    private record AdminContext(Long id, String token) {}

    /**
     * Helper: crea un usuario ADMIN en DB y genera un JWT para el.
     *
     * QUÉ HACE: Persiste un User con Role.ADMIN, genera un JWT
     * firmado usando JwtService, y retorna el ID + token.
     *
     * POR QUÉ este helper:
     * - Los tests de escritura necesitan un ADMIN autenticado.
     * - Centraliza la creacion para evitar duplicacion.
     * - El password se hashea con BCrypt real (mismo que en produccion).
     *
     * @return AdminContext con ID del admin y su JWT
     */
    private AdminContext createAdminContext() {
        User admin = new User("admin", "admin@mundolimpio.com",
                passwordEncoder.encode("admin123"), Role.ADMIN);
        admin = userRepository.save(admin);
        String token = jwtService.generateToken(admin);
        return new AdminContext(admin.getId(), token);
    }

    /**
     * Helper: crea headers HTTP con token JWT y Content-Type JSON.
     *
     * @param token JWT del ADMIN autenticado
     * @return HttpHeaders con Authorization Bearer y Content-Type application/json
     */
    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @BeforeEach
    void setUp() {
        // Configurar soporte para HTTP PATCH (HttpURLConnection no lo soporta)
        RestTemplate rt = restTemplate.getRestTemplate();
        rt.setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        userRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void shouldCreateProductSuccessfully() {
        AdminContext admin = createAdminContext();
        ProductRequest request = new ProductRequest("DETERGENTE-001", "Detergente Multiusos 500ml", new BigDecimal("10.50"));
        HttpEntity<ProductRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> response = restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity, ProductResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        assertEquals("DETERGENTE-001", response.getBody().sku());
        assertEquals("Detergente Multiusos 500ml", response.getBody().name());
        assertEquals(new BigDecimal("10.50"), response.getBody().minPrice());
        assertTrue(response.getBody().active());
    }

    @Test
    void shouldReturnConflictWhenSkuAlreadyExists() {
        AdminContext admin = createAdminContext();
        ProductRequest request = new ProductRequest("CLORO-001", "Cloro", new BigDecimal("8.00"));
        HttpEntity<ProductRequest> entity1 = new HttpEntity<>(request, authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity1, ProductResponse.class);

        HttpEntity<ProductRequest> entity2 = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity2, String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_ALREADY_EXISTS"));
    }

    @Test
    void shouldReturnBadRequestWhenValidationFails() {
        AdminContext admin = createAdminContext();
        String invalidJson = """
                {
                    "sku": "",
                    "name": "Producto",
                    "minPrice": 10.50
                }
                """;

        HttpEntity<String> entity = new HttpEntity<>(invalidJson, authHeaders(admin.token()));
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnBadRequestForInvalidSkuPattern() {
        AdminContext admin = createAdminContext();
        ProductRequest request = new ProductRequest("detergente-001", "Detergente", new BigDecimal("10.00"));
        HttpEntity<ProductRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("VALIDATION_ERROR"));
        assertTrue(response.getBody().contains("uppercase"));
    }

    @Test
    void shouldReturnBadRequestForNegativePrice() {
        AdminContext admin = createAdminContext();
        ProductRequest request = new ProductRequest("PRODUCTO-001", "Producto", new BigDecimal("-5.00"));
        HttpEntity<ProductRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("VALIDATION_ERROR"));
    }

    @Test
    void shouldGetProductByIdSuccessfully() {
        AdminContext admin = createAdminContext();
        ProductRequest createRequest = new ProductRequest("JABON-001", "Jabón Antibacterial", new BigDecimal("5.25"));
        HttpEntity<ProductRequest> createEntity = new HttpEntity<>(createRequest, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> createResponse = restTemplate.exchange("/api/v1/products", HttpMethod.POST, createEntity, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        ResponseEntity<ProductResponse> response = restTemplate.getForEntity("/api/v1/products/" + productId, ProductResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(productId, response.getBody().id());
        assertEquals("JABON-001", response.getBody().sku());
    }

    @Test
    void shouldGetProductBySkuSuccessfully() {
        AdminContext admin = createAdminContext();
        ProductRequest request = new ProductRequest("DESINFECTANTE-001", "Desinfectante", new BigDecimal("7.50"));
        HttpEntity<ProductRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity, ProductResponse.class);

        ResponseEntity<ProductResponse> response = restTemplate.getForEntity("/api/v1/products/sku/DESINFECTANTE-001", ProductResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DESINFECTANTE-001", response.getBody().sku());
    }

    @Test
    void shouldReturnNotFoundForNonExistentId() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/products/999999", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldReturnNotFoundForNonExistentSku() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/products/sku/NO-EXISTE", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldGetAllActiveProductsSuccessfully() {
        AdminContext admin = createAdminContext();
        ProductRequest request1 = new ProductRequest("PROD-001", "Producto 1", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("PROD-002", "Producto 2", new BigDecimal("20.00"));
        HttpEntity<ProductRequest> entity1 = new HttpEntity<>(request1, authHeaders(admin.token()));
        HttpEntity<ProductRequest> entity2 = new HttpEntity<>(request2, authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity1, ProductResponse.class);
        restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity2, ProductResponse.class);

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/products", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertEquals(2, content.size());
    }

    @Test
    void shouldGetAllProductsIncludingInactive() {
        AdminContext admin = createAdminContext();
        ProductRequest request1 = new ProductRequest("ACTIVE-001", "Activo", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("INACTIVE-001", "Inactivo", new BigDecimal("20.00"));

        HttpEntity<ProductRequest> entity1 = new HttpEntity<>(request1, authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity1, ProductResponse.class);
        HttpEntity<ProductRequest> entity2 = new HttpEntity<>(request2, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> response2 = restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity2, ProductResponse.class);
        Long inactiveId = response2.getBody().id();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products/" + inactiveId, HttpMethod.DELETE, deleteEntity, Void.class);
        // WHAT: /all ahora requiere ADMIN o STOCK_MANAGER (sync-frontend-backend)
        // WHY: El endpoint /all estaba expuesto sin auth via wildcard en SecurityConfig
        HttpEntity<Void> getAllEntity = new HttpEntity<>(authHeaders(admin.token()));
        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/products/all", HttpMethod.GET, getAllEntity, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertEquals(2, content.size());
    }

    @Test
    void shouldUpdateProductSuccessfully() {
        AdminContext admin = createAdminContext();
        ProductRequest createRequest = new ProductRequest("UPDATE-001", "Original", new BigDecimal("15.00"));
        HttpEntity<ProductRequest> createEntity = new HttpEntity<>(createRequest, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> createResponse = restTemplate.exchange("/api/v1/products", HttpMethod.POST, createEntity, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        ProductRequest updateRequest = new ProductRequest("UPDATE-002", "Actualizado", new BigDecimal("20.00"));
        HttpEntity<ProductRequest> updateEntity = new HttpEntity<>(updateRequest, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> response = restTemplate.exchange("/api/v1/products/" + productId, HttpMethod.PUT, updateEntity, ProductResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UPDATE-002", response.getBody().sku());
        assertEquals("Actualizado", response.getBody().name());
        assertEquals(new BigDecimal("20.00"), response.getBody().minPrice());
    }

    @Test
    void shouldReturnConflictWhenUpdateSkuToExistingOne() {
        AdminContext admin = createAdminContext();
        ProductRequest request1 = new ProductRequest("UNIQUE-001", "Producto 1", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("UNIQUE-002", "Producto 2", new BigDecimal("20.00"));

        HttpEntity<ProductRequest> entity1 = new HttpEntity<>(request1, authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity1, ProductResponse.class);
        HttpEntity<ProductRequest> entity2 = new HttpEntity<>(request2, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> response2 = restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity2, ProductResponse.class);
        Long product2Id = response2.getBody().id();

        ProductRequest updateRequest = new ProductRequest("UNIQUE-001", "Producto 2 Actualizado", new BigDecimal("25.00"));
        HttpEntity<ProductRequest> updateEntity = new HttpEntity<>(updateRequest, authHeaders(admin.token()));
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/products/" + product2Id, HttpMethod.PUT, updateEntity, String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_ALREADY_EXISTS"));
    }

    @Test
    void shouldSoftDeleteProductSuccessfully() {
        AdminContext admin = createAdminContext();
        ProductRequest request = new ProductRequest("DELETE-001", "Para borrar", new BigDecimal("10.00"));
        HttpEntity<ProductRequest> createEntity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> createResponse = restTemplate.exchange("/api/v1/products", HttpMethod.POST, createEntity, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(admin.token()));
        ResponseEntity<Void> response = restTemplate.exchange("/api/v1/products/" + productId, HttpMethod.DELETE, deleteEntity, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var productFromDb = productRepository.findById(productId);
        assertTrue(productFromDb.isPresent());
        assertFalse(productFromDb.get().getActive());
    }

    @Test
    void shouldNotIncludeDeletedProductInActiveList() {
        AdminContext admin = createAdminContext();
        ProductRequest request1 = new ProductRequest("ACTIVE-PROD", "Activo", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("DELETE-PROD", "Será borrado", new BigDecimal("20.00"));

        HttpEntity<ProductRequest> entity1 = new HttpEntity<>(request1, authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity1, ProductResponse.class);
        HttpEntity<ProductRequest> entity2 = new HttpEntity<>(request2, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> response2 = restTemplate.exchange("/api/v1/products", HttpMethod.POST, entity2, ProductResponse.class);
        Long deleteId = response2.getBody().id();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products/" + deleteId, HttpMethod.DELETE, deleteEntity, Void.class);
        ResponseEntity<Map> allActiveResponse = restTemplate.getForEntity("/api/v1/products", Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) allActiveResponse.getBody().get("content");
        assertEquals(1, content.size());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistentProduct() {
        AdminContext admin = createAdminContext();
        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(admin.token()));
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/products/999999", HttpMethod.DELETE, deleteEntity, String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldReactivateProductSuccessfully() {
        AdminContext admin = createAdminContext();
        ProductRequest request = new ProductRequest("REACTIV-001", "Para reactivar", new BigDecimal("10.00"));
        HttpEntity<ProductRequest> createEntity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> createResponse = restTemplate.exchange("/api/v1/products", HttpMethod.POST, createEntity, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products/" + productId, HttpMethod.DELETE, deleteEntity, Void.class);
        HttpEntity<Void> patchEntity = new HttpEntity<>(authHeaders(admin.token()));
        ResponseEntity<Void> response = restTemplate.exchange("/api/v1/products/" + productId + "/reactivate", HttpMethod.PATCH, patchEntity, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var productFromDb = productRepository.findById(productId);
        assertTrue(productFromDb.isPresent());
        assertTrue(productFromDb.get().getActive());
    }

    @Test
    void shouldIncludeReactivatedProductInActiveList() {
        AdminContext admin = createAdminContext();
        ProductRequest request = new ProductRequest("BACK-TO-ACTIVE", "Vuelve a ser activo", new BigDecimal("10.00"));
        HttpEntity<ProductRequest> createEntity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<ProductResponse> createResponse = restTemplate.exchange("/api/v1/products", HttpMethod.POST, createEntity, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products/" + productId, HttpMethod.DELETE, deleteEntity, Void.class);
        HttpEntity<Void> patchEntity = new HttpEntity<>(authHeaders(admin.token()));
        restTemplate.exchange("/api/v1/products/" + productId + "/reactivate", HttpMethod.PATCH, patchEntity, Void.class);

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/products", Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertEquals(1, content.size());
    }
}