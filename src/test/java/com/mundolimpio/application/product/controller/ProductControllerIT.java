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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de Integración para ProductController con CRUD Completo
 *
 * Usa Testcontainers para levantar un MySQL real en Docker.
 * Prueba el flujo completo: HTTP Request → Controller → Service → Repository → MySQL (Real)
 *
 * Cada test obtiene una base de datos limpia y aislada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductControllerIT {

    /**
     * Contenedor MySQL que se levanta automáticamente.
     * Spring Boot lo detecta y configura la conexión automáticamente.
     */
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mundolimpio_test")
            .withUsername("test_user")
            .withPassword("test_password");

    /**
     * DynamicPropertySource configura las propiedades de Spring Boot
     * para que use el contenedor MySQL.
     */
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

    /**
     * Se ejecuta antes de cada test para limpiar la base de datos.
     */
    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    // ==================== CREATE TESTS ====================

    @Test
    void shouldCreateProductSuccessfully() {
        // Arrange
        ProductRequest request = new ProductRequest(
                "DETERGENTE-001",
                "Detergente Multiusos 500ml",
                new BigDecimal("10.50")
        );

        // Act
        ResponseEntity<ProductResponse> response = restTemplate.postForEntity(
                "/api/v1/products",
                request,
                ProductResponse.class
        );

        // Assert
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
        // Arrange: crear el primer producto
        ProductRequest request = new ProductRequest("CLORO-001", "Cloro", new BigDecimal("8.00"));
        restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);

        // Act: intentar crear otro con el mismo SKU
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/products",
                request,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_ALREADY_EXISTS"));
    }

    @Test
    void shouldReturnBadRequestWhenValidationFails() {
        // Arrange: request con SKU vacío
        String invalidJson = """
                {
                    "sku": "",
                    "name": "Producto",
                    "minPrice": 10.50
                }
                """;

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/products",
                invalidJson,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnBadRequestForInvalidSkuPattern() {
        // Arrange: request con SKU con caracteres inválidos (minúsculas, espacios)
        ProductRequest request = new ProductRequest(
                "detergente-001", // ❌ minúsculas no permitidas
                "Detergente",
                new BigDecimal("10.00")
        );

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/products",
                request,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("VALIDATION_ERROR"));
        assertTrue(response.getBody().contains("uppercase"));
    }

    @Test
    void shouldReturnBadRequestForNegativePrice() {
        // Arrange: request con precio negativo
        ProductRequest request = new ProductRequest(
                "PRODUCTO-001",
                "Producto",
                new BigDecimal("-5.00") // ❌ negativo
        );

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/products",
                request,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("VALIDATION_ERROR"));
    }

    // ==================== READ TESTS ====================

    @Test
    void shouldGetProductByIdSuccessfully() {
        // Arrange: crear un producto
        ProductRequest createRequest = new ProductRequest(
                "JABON-001",
                "Jabón Antibacterial",
                new BigDecimal("5.25")
        );
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/products",
                createRequest,
                ProductResponse.class
        );
        Long productId = createResponse.getBody().id();

        // Act: obtener por ID
        ResponseEntity<ProductResponse> response = restTemplate.getForEntity(
                "/api/v1/products/" + productId,
                ProductResponse.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(productId, response.getBody().id());
        assertEquals("JABON-001", response.getBody().sku());
    }

    @Test
    void shouldGetProductBySkuSuccessfully() {
        // Arrange: crear un producto
        ProductRequest request = new ProductRequest(
                "DESINFECTANTE-001",
                "Desinfectante",
                new BigDecimal("7.50")
        );
        restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);

        // Act: obtener por SKU
        ResponseEntity<ProductResponse> response = restTemplate.getForEntity(
                "/api/v1/products/sku/DESINFECTANTE-001",
                ProductResponse.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DESINFECTANTE-001", response.getBody().sku());
    }

    @Test
    void shouldReturnNotFoundForNonExistentId() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/products/999999",
                String.class
        );

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldReturnNotFoundForNonExistentSku() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/products/sku/NO-EXISTE",
                String.class
        );

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldGetAllActiveProductsSuccessfully() {
        // Arrange: crear varios productos
        ProductRequest request1 = new ProductRequest("PROD-001", "Producto 1", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("PROD-002", "Producto 2", new BigDecimal("20.00"));
        restTemplate.postForEntity("/api/v1/products", request1, ProductResponse.class);
        restTemplate.postForEntity("/api/v1/products", request2, ProductResponse.class);

        // Act
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/products",
                List.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldGetAllProductsIncludingInactive() {
        // Arrange: crear 2 productos y marcar 1 como inactivo
        ProductRequest request1 = new ProductRequest("ACTIVE-001", "Activo", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("INACTIVE-001", "Inactivo", new BigDecimal("20.00"));

        ResponseEntity<ProductResponse> response1 = restTemplate.postForEntity("/api/v1/products", request1, ProductResponse.class);
        ResponseEntity<ProductResponse> response2 = restTemplate.postForEntity("/api/v1/products", request2, ProductResponse.class);

        Long inactiveId = response2.getBody().id();

        // Marcar como inactivo
        restTemplate.delete("/api/v1/products/" + inactiveId);

        // Act: obtener TODOS (activos e inactivos)
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/products/all",
                List.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size()); // Ambos productos (1 activo, 1 inactivo)
    }

    // ==================== UPDATE TESTS ====================

    @Test
    void shouldUpdateProductSuccessfully() {
        // Arrange: crear un producto
        ProductRequest createRequest = new ProductRequest("UPDATE-001", "Original", new BigDecimal("15.00"));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/products",
                createRequest,
                ProductResponse.class
        );
        Long productId = createResponse.getBody().id();

        // Preparar actualización
        ProductRequest updateRequest = new ProductRequest("UPDATE-002", "Actualizado", new BigDecimal("20.00"));

        // Act
        ResponseEntity<ProductResponse> response = restTemplate.exchange(
                "/api/v1/products/" + productId,
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(updateRequest),
                ProductResponse.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UPDATE-002", response.getBody().sku());
        assertEquals("Actualizado", response.getBody().name());
        assertEquals(new BigDecimal("20.00"), response.getBody().minPrice());
    }

    @Test
    void shouldReturnConflictWhenUpdateSkuToExistingOne() {
        // Arrange: crear 2 productos
        ProductRequest request1 = new ProductRequest("UNIQUE-001", "Producto 1", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("UNIQUE-002", "Producto 2", new BigDecimal("20.00"));

        ResponseEntity<ProductResponse> response1 = restTemplate.postForEntity("/api/v1/products", request1, ProductResponse.class);
        ResponseEntity<ProductResponse> response2 = restTemplate.postForEntity("/api/v1/products", request2, ProductResponse.class);

        Long product2Id = response2.getBody().id();

        // Intentar actualizar producto 2 con SKU del producto 1
        ProductRequest updateRequest = new ProductRequest("UNIQUE-001", "Producto 2 Actualizado", new BigDecimal("25.00"));

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/products/" + product2Id,
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(updateRequest),
                String.class
        );

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_ALREADY_EXISTS"));
    }

    // ==================== DELETE TESTS ====================

    @Test
    void shouldSoftDeleteProductSuccessfully() {
        // Arrange: crear un producto
        ProductRequest request = new ProductRequest("DELETE-001", "Para borrar", new BigDecimal("10.00"));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/products",
                request,
                ProductResponse.class
        );
        Long productId = createResponse.getBody().id();

        // Act: soft delete
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/products/" + productId,
                org.springframework.http.HttpMethod.DELETE,
                null,
                Void.class
        );

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verificar que el producto está inactivo en la BD
        var productFromDb = productRepository.findById(productId);
        assertTrue(productFromDb.isPresent());
        assertFalse(productFromDb.get().getActive()); // ✅ Está marcado como inactivo
    }

    @Test
    void shouldNotIncludeDeletedProductInActiveList() {
        // Arrange: crear 2 productos y borrar 1
        ProductRequest request1 = new ProductRequest("ACTIVE-PROD", "Activo", new BigDecimal("10.00"));
        ProductRequest request2 = new ProductRequest("DELETE-PROD", "Será borrado", new BigDecimal("20.00"));

        restTemplate.postForEntity("/api/v1/products", request1, ProductResponse.class);
        ResponseEntity<ProductResponse> response2 = restTemplate.postForEntity("/api/v1/products", request2, ProductResponse.class);

        Long deleteId = response2.getBody().id();

        // Act: borrar el segundo producto
        restTemplate.delete("/api/v1/products/" + deleteId);

        // Act: obtener activos
        ResponseEntity<List> allActiveResponse = restTemplate.getForEntity(
                "/api/v1/products",
                List.class
        );

        // Assert
        assertEquals(1, allActiveResponse.getBody().size()); // Solo 1 activo
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistentProduct() {
        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/products/999999",
                org.springframework.http.HttpMethod.DELETE,
                null,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("PRODUCT_NOT_FOUND"));
    }

    // ==================== REACTIVATE TESTS ====================

    @Test
    void shouldReactivateProductSuccessfully() {
        // Arrange: crear y marcar como inactivo
        ProductRequest request = new ProductRequest("REACTIV-001", "Para reactivar", new BigDecimal("10.00"));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/products",
                request,
                ProductResponse.class
        );
        Long productId = createResponse.getBody().id();

        // Soft delete
        restTemplate.delete("/api/v1/products/" + productId);

        // Act: reactivar
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/products/" + productId + "/reactivate",
                org.springframework.http.HttpMethod.PATCH,
                null,
                Void.class
        );

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verificar que el producto está activo nuevamente
        var productFromDb = productRepository.findById(productId);
        assertTrue(productFromDb.isPresent());
        assertTrue(productFromDb.get().getActive()); // ✅ Está activo nuevamente
    }

    @Test
    void shouldIncludeReactivatedProductInActiveList() {
        // Arrange: crear, borrar y reactivar
        ProductRequest request = new ProductRequest("BACK-TO-ACTIVE", "Vuelve a ser activo", new BigDecimal("10.00"));
        ResponseEntity<ProductResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/products",
                request,
                ProductResponse.class
        );
        Long productId = createResponse.getBody().id();

        restTemplate.delete("/api/v1/products/" + productId);
        restTemplate.exchange(
                "/api/v1/products/" + productId + "/reactivate",
                org.springframework.http.HttpMethod.PATCH,
                null,
                Void.class
        );

        // Act: obtener activos
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/products",
                List.class
        );

        // Assert
        assertEquals(1, response.getBody().size()); // ✅ El producto reactivado está en la lista
    }
}
