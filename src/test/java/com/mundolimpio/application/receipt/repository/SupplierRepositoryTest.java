package com.mundolimpio.application.receipt.repository;

import com.mundolimpio.application.receipt.domain.Supplier;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración para SupplierRepository.
 * 
 * POR QUÉ extiende AbstractIntegrationTest:
 * - findByName usa una query JPA derivada que debe ejecutarse contra PostgreSQL real.
 * - Necesitamos verificar que el índice idx_supplier_name funciona correctamente.
 * 
 * PENDING: Requiere Docker corriendo para Testcontainers.
 */
class SupplierRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SupplierRepository supplierRepository;

    /**
     * Test 1.4.1 RED: findByNombre debe retornar el Supplier correcto.
     * Triangulación: un nombre que existe y uno que no.
     */
    @Test
    void testFindByName_ReturnsSupplier_WhenExists() {
        // Given
        Supplier saved = supplierRepository.save(new Supplier("Distribuidora ABC"));
        supplierRepository.save(new Supplier("Otra Empresa"));

        // When
        Optional<Supplier> found = supplierRepository.findByName("Distribuidora ABC");
        Optional<Supplier> notFound = supplierRepository.findByName("Inexistente");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Distribuidora ABC");
        assertThat(notFound).isEmpty();
    }

    /**
     * Test 1.4.2: El ID debe ser generado automáticamente por la DB.
     */
    @Test
    void testSave_GeneratesId() {
        Supplier saved = supplierRepository.save(new Supplier("Proveedor X"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isGreaterThan(0);
    }
}
