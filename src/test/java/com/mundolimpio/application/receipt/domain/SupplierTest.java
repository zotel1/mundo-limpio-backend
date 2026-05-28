package com.mundolimpio.application.receipt.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para la entidad Supplier.
 * 
 * POR QUÉ tests unitarios puros (sin Spring):
 * - Supplier es una entidad JPA simple, sin dependencias externas.
 * - No necesitamos contenedor PostgreSQL para probar constructores y getters.
 * - JPA se encarga del mapeo ORM — eso se prueba con tests de integración.
 */
class SupplierTest {

    /**
     * Test 1.3.1 RED: Constructor debe setear name y createdAt automáticamente.
     * Triangulamos con dos proveedores distintos para asegurar que no hay hardcoding.
     */
    @Test
    void testConstructor_SetsNameAndCreatedAt() {
        // Given
        String name1 = "Proveedor A";
        String name2 = "Distribuidora XYZ";

        // When
        Supplier supplier1 = new Supplier(name1);
        Supplier supplier2 = new Supplier(name2);

        // Then
        assertEquals(name1, supplier1.getName());
        assertEquals(name2, supplier2.getName());
        assertNotNull(supplier1.getCreatedAt());
        assertNotNull(supplier2.getCreatedAt());
    }

    /**
     * Test 1.3.2: createdAt debe ser cercano a now() en el momento de creación.
     * Verifica que el timestamp se setea en el constructor y no después.
     */
    @Test
    void testCreatedAt_IsSetAtConstructionTime() {
        // Given
        LocalDateTime before = LocalDateTime.now();

        // When
        Supplier supplier = new Supplier("Proveedor Test");

        // Then
        LocalDateTime after = LocalDateTime.now();
        assertNotNull(supplier.getCreatedAt());
        // Verificamos que está entre before y after (con 1 segundo de tolerancia)
        assertFalse(supplier.getCreatedAt().isBefore(before.minusSeconds(1)));
        assertFalse(supplier.getCreatedAt().isAfter(after.plusSeconds(1)));
    }

    /**
     * Test 1.3.3: El ID debe ser null hasta que JPA lo persista.
     * JPA setea el ID después de INSERT — en el constructor, siempre es null.
     */
    @Test
    void testId_IsNullBeforePersistence() {
        Supplier supplier = new Supplier("Proveedor Test");
        assertNull(supplier.getId());
    }
}
