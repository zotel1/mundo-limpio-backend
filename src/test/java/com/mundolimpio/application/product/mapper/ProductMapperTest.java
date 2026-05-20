package com.mundolimpio.application.product.mapper;

import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.dto.ProductRequest;
import com.mundolimpio.application.product.dto.ProductResponse;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios para ProductMapper.
 *
 * QUE HACE: Verifica la conversión entity ↔ DTO del ProductMapper
 * usando el contexto real de Spring con PostgreSQL via Testcontainers.
 *
 * POR QUÉ AbstractIntegrationTest:
 * - ProductMapper es un @Component de Spring. Verificar que se inyecta
 *   correctamente con @Autowired prueba que el wiring funciona.
 * - Usa PostgreSQL real (Testcontainers) para consistencia con entorno.
 *
 * DIFFERENCES con SaleMapperTest:
 * - SaleMapperTest tiene 2 tests mínimos (solo verifica autowire).
 * - ProductMapperTest tiene 4 tests con assertions de campos específicos.
 * - Ambos extienden AbstractIntegrationTest y usan AssertJ.
 */
class ProductMapperTest extends AbstractIntegrationTest {

    @Autowired
    private ProductMapper productMapper;

    // ==================== toEntity TESTS ====================

    /**
     * Test 1: toEntity debe mapear todos los campos del ProductRequest a la entidad Product.
     *
     * QUE VERIFICA:
     * - El mapper no es null (Spring lo inyectó correctamente).
     * - sku, name, minPrice se transfieren del request a la entidad.
     * - active se setea a true por defecto.
     */
    @Test
    void toEntity_ShouldMapAllFields() {
        // Given: un ProductRequest con datos conocidos
        ProductRequest request = new ProductRequest("SKU-001", "Test Product", new BigDecimal("15.99"));

        // When: convertimos a entidad
        Product entity = productMapper.toEntity(request);

        // Then: todos los campos deben mapearse correctamente
        assertThat(entity).isNotNull();
        assertThat(entity.getSku()).isEqualTo("SKU-001");
        assertThat(entity.getName()).isEqualTo("Test Product");
        assertThat(entity.getMinPrice()).isEqualByComparingTo(new BigDecimal("15.99"));
        assertThat(entity.getActive()).isTrue();
    }

    /**
     * Test 2: toEntity debe siempre setear active=true al crear una entidad.
     *
     * QUE VERIFICA:
     * - Independientemente del request, el campo active se inicializa en true.
     * - Esto es el comportamiento por defecto del mapper (no es responsabilidad del request).
     *
     * POR QUÉ test separado: confirma explícitamente la convención de que
     * los productos nuevos se crean activos, sin depender del test anterior.
     */
    @Test
    void toEntity_ShouldSetActiveToTrueByDefault() {
        // Given: un ProductRequest con datos distintos al test anterior
        ProductRequest request = new ProductRequest("SKU-DEFAULT", "Default Product", BigDecimal.ONE);

        // When: convertimos a entidad
        Product entity = productMapper.toEntity(request);

        // Then: active debe ser true (valor por defecto del mapper)
        assertThat(entity.getActive()).isTrue();
    }

    // ==================== toResponse TESTS ====================

    /**
     * Test 3: toResponse debe mapear todos los campos de la entidad al DTO de respuesta.
     *
     * QUE VERIFICA:
     * - id, sku, name, minPrice, active se transfieren correctamente.
     * - El DTO resultante es un record con los valores esperados.
     */
    @Test
    void toResponse_ShouldMapAllFieldsFromEntity() {
        // Given: una entidad Product con todos los campos y active=false
        Product entity = new Product(1L, "SKU-ENT", "Entity Product", new BigDecimal("25.50"), false);

        // When: convertimos a response
        ProductResponse response = productMapper.toResponse(entity);

        // Then: todos los campos deben coincidir con la entidad
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sku()).isEqualTo("SKU-ENT");
        assertThat(response.name()).isEqualTo("Entity Product");
        assertThat(response.minPrice()).isEqualByComparingTo(new BigDecimal("25.50"));
        assertThat(response.active()).isFalse();
    }

    /**
     * Test 4: toResponse debe preservar el campo active=true cuando la entidad lo tiene.
     *
     * QUE VERIFICA:
     * - active=true se mapea correctamente (no se convierte a false).
     * - Confirma que el mapper preserva el valor real de la entidad, no lo sobreescribe.
     *
     * POR QUÉ test separado: triangula el test anterior (active=false) con
     * el caso opuesto (active=true) para garantizar que el campo se preserva
     * en ambas direcciones.
     */
    @Test
    void toResponse_ShouldPreserveActiveField() {
        // Given: una entidad con active=true (caso opuesto al test 3)
        Product entity = new Product(2L, "SKU-ACTIVE", "Active Product", new BigDecimal("5.00"), true);

        // When: convertimos a response
        ProductResponse response = productMapper.toResponse(entity);

        // Then: active debe ser true (preservado de la entidad)
        assertThat(response.active()).isTrue();
    }
}
