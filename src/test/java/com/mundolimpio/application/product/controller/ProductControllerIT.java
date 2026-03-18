package com.mundolimpio.application.product.controller;

import com.mundolimpio.application.product.dto.ProductRequest;
import com.mundolimpio.application.product.dto.ProductResponse;
import com.mundolimpio.application.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductControllerIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mundolimpio_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void shouldCreateProductSuccessfully() {
        ProductRequest request = new ProductRequest("DETERGENTE-001", "Detergente Multiusos 500ml", new BigDecimal("10.50"));
        ResponseEntity<ProductResponse> response = restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);

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
        ProductRequest request = new ProductRequest("CLORO-001", "Cloro", new BigDecimal("8.00"));
        restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/products", request, String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_ALREADY_EXISTS"));
    }

    @Test
    void shouldReturnBadRequestWhenValidationFails() {
        String invalidJson = """
                {
                    "sku": "",
                    "name": "Producto",
                    "minPrice": 10.50
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/products", invalidJson, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnBadRequestForInvalidSkuPattern() {
        ProductRequest request = new ProductRequest("detergente-001", "Detergente", new BigDecimal("10.00"));
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/products", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("VALIDATION_ERROR"));
        assertTrue(response.getBody().contains("uppercase"));
    }

    @Test
    void shouldReturnBadRequestForNegativePrice() {
        ProductRequest request = new ProductRequest("PRODUCTO-001", "Producto", new BigDecimal("-5.00"));
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/products", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("VALIDATION_ERROR"));
    }

    @Test
    void shouldGetProductByIdSuccessfully() {
        ProductRequest createRequest = new ProductRequest("JABON-001", "Jabón Antibacterial", new BigDecimal("5.25"));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity("/api/v1/products", createRequest, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        ResponseEntity<ProductResponse> response = restTemplate.getForEntity("/api/v1/products/" + productId, ProductResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(productId, response.getBody().id());
        assertEquals("JABON-001", response.getBody().sku());
    }

    @Test
    void shouldGetProductBySkuSuccessfully() {
        ProductRequest request = new ProductRequest("DESINFECTANTE-001", "Desinfectante", new BigDecimal("7.50"));
        restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);

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
        ProductRequest request1 = new ProductRequest("PROD-001", "Producto 1", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("PROD-002", "Producto 2", new BigDecimal("20.00"));
        restTemplate.postForEntity("/api/v1/products", request1, ProductResponse.class);
        restTemplate.postForEntity("/api/v1/products", request2, ProductResponse.class);

        ResponseEntity<List> response = restTemplate.getForEntity("/api/v1/products", List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldGetAllProductsIncludingInactive() {
        ProductRequest request1 = new ProductRequest("ACTIVE-001", "Activo", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("INACTIVE-001", "Inactivo", new BigDecimal("20.00"));

        restTemplate.postForEntity("/api/v1/products", request1, ProductResponse.class);
        ResponseEntity<ProductResponse> response2 = restTemplate.postForEntity("/api/v1/products", request2, ProductResponse.class);
        Long inactiveId = response2.getBody().id();

        restTemplate.delete("/api/v1/products/" + inactiveId);
        ResponseEntity<List> response = restTemplate.getForEntity("/api/v1/products/all", List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldUpdateProductSuccessfully() {
        ProductRequest createRequest = new ProductRequest("UPDATE-001", "Original", new BigDecimal("15.00"));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity("/api/v1/products", createRequest, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        ProductRequest updateRequest = new ProductRequest("UPDATE-002", "Actualizado", new BigDecimal("20.00"));
        ResponseEntity<ProductResponse> response = restTemplate.exchange("/api/v1/products/" + productId, org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(updateRequest), ProductResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UPDATE-002", response.getBody().sku());
        assertEquals("Actualizado", response.getBody().name());
        assertEquals(new BigDecimal("20.00"), response.getBody().minPrice());
    }

    @Test
    void shouldReturnConflictWhenUpdateSkuToExistingOne() {
        ProductRequest request1 = new ProductRequest("UNIQUE-001", "Producto 1", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("UNIQUE-002", "Producto 2", new BigDecimal("20.00"));

        restTemplate.postForEntity("/api/v1/products", request1, ProductResponse.class);
        ResponseEntity<ProductResponse> response2 = restTemplate.postForEntity("/api/v1/products", request2, ProductResponse.class);
        Long product2Id = response2.getBody().id();

        ProductRequest updateRequest = new ProductRequest("UNIQUE-001", "Producto 2 Actualizado", new BigDecimal("25.00"));
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/products/" + product2Id, org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(updateRequest), String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_ALREADY_EXISTS"));
    }

    @Test
    void shouldSoftDeleteProductSuccessfully() {
        ProductRequest request = new ProductRequest("DELETE-001", "Para borrar", new BigDecimal("10.00"));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        ResponseEntity<Void> response = restTemplate.exchange("/api/v1/products/" + productId, org.springframework.http.HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var productFromDb = productRepository.findById(productId);
        assertTrue(productFromDb.isPresent());
        assertFalse(productFromDb.get().getActive());
    }

    @Test
    void shouldNotIncludeDeletedProductInActiveList() {
        ProductRequest request1 = new ProductRequest("ACTIVE-PROD", "Activo", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("DELETE-PROD", "Será borrado", new BigDecimal("20.00"));

        restTemplate.postForEntity("/api/v1/products", request1, ProductResponse.class);
        ResponseEntity<ProductResponse> response2 = restTemplate.postForEntity("/api/v1/products", request2, ProductResponse.class);
        Long deleteId = response2.getBody().id();

        restTemplate.delete("/api/v1/products/" + deleteId);
        ResponseEntity<List> allActiveResponse = restTemplate.getForEntity("/api/v1/products", List.class);

        assertEquals(1, allActiveResponse.getBody().size());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistentProduct() {
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/products/999999", org.springframework.http.HttpMethod.DELETE, null, String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldReactivateProductSuccessfully() {
        ProductRequest request = new ProductRequest("REACTIV-001", "Para reactivar", new BigDecimal("10.00"));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        restTemplate.delete("/api/v1/products/" + productId);
        ResponseEntity<Void> response = restTemplate.exchange("/api/v1/products/" + productId + "/reactivate", org.springframework.http.HttpMethod.PATCH, null, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var productFromDb = productRepository.findById(productId);
        assertTrue(productFromDb.isPresent());
        assertTrue(productFromDb.get().getActive());
    }

    @Test
    void shouldIncludeReactivatedProductInActiveList() {
        ProductRequest request = new ProductRequest("BACK-TO-ACTIVE", "Vuelve a ser activo", new BigDecimal("10.00"));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);
        Long productId = createResponse.getBody().id();

        restTemplate.delete("/api/v1/products/" + productId);
        restTemplate.exchange("/api/v1/products/" + productId + "/reactivate", org.springframework.http.HttpMethod.PATCH, null, Void.class);

        ResponseEntity<List> response = restTemplate.getForEntity("/api/v1/products", List.class);

        assertEquals(1, response.getBody().size());
    }
}
