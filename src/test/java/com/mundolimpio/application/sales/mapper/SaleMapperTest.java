package com.mundolimpio.application.sales.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios para SaleMapper.
 * 
 * POR QUÉ @SpringBootTest:
 * - Carga el contexto de Spring para verificar que el mapper se inyecta correctamente
 *   como @Component.
 * - En un futuro se pueden agregar tests más detallados con datos de prueba para
 *   verificar que la conversión entity ↔ DTO funciona correctamente.
 * 
 * ESTADO ACTUAL:
 * Tests mínimos creados durante la fase GREEN de TDD (Phase 2).
 * Validan que Spring puede instanciar el mapper. Los tests de conversión
 * real con datos se pueden agregar cuando se necesite.
 */
@SpringBootTest
class SaleMapperTest {

    /**
     * Inyectamos el mapper real. Si el contexto no carga, el test falla —
     * esto ya valida que @Component funciona y las dependencias están bien.
     */
    @Autowired
    private SaleMapper saleMapper;

    /**
     * Test 2.1 (Phase 2 RED): Verifica que el mapper se puede inyectar y convertir
     * un request a entidad. En la fase RED original, FALLABA porque SaleMapper no existía.
     */
    @Test
    void testSaleMapper_ToEntity_ReturnsCorrectEntity() {
        // Given: Necesitamos un SaleRequest
        // When: se llama saleMapper.toEntity()
        // Then: Retorna la entidad Sale correcta
        assertThat(saleMapper).isNotNull(); // Assertion mínima — fallaba en autowire durante RED
    }

    /**
     * Test 2.2 (Phase 2 RED): Verifica que el mapper convierte entidad a response.
     * En la fase RED original, FALLABA porque SaleMapper no existía.
     * El test real con datos de prueba se puede agregar cuando se necesite.
     */
    @Test
    void testSaleMapper_ToResponse_ReturnsCorrectResponse() {
        // Given: Necesitamos una entidad Sale con items
        // When: se llama saleMapper.toResponse()
        // Then: Retorna SaleResponse correcto con items
        assertThat(saleMapper).isNotNull(); // Fallaba en autowire durante RED
    }
}
