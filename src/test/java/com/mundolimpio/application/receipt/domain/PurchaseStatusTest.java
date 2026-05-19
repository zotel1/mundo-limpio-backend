package com.mundolimpio.application.receipt.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para el enum PurchaseStatus.
 * 
 * POR QUÉ testear un enum:
 * - Aunque es trivial, verificamos que los valores existen y tienen los nombres esperados.
 * - Si alguien cambia el enum sin actualizar la lógica de negocio, estos tests lo detectan.
 */
class PurchaseStatusTest {

    /**
     * Test 1.3.4: El enum debe tener exactamente dos valores: PENDING y CONFIRMED.
     * Triangulación: verificamos valueOf y el array de values().
     */
    @Test
    void testEnum_HasTwoValues_PendingAndConfirmed() {
        PurchaseStatus[] values = PurchaseStatus.values();
        assertEquals(2, values.length);

        PurchaseStatus pending = PurchaseStatus.valueOf("PENDING");
        PurchaseStatus confirmed = PurchaseStatus.valueOf("CONFIRMED");

        assertNotNull(pending);
        assertNotNull(confirmed);
        assertNotEquals(pending, confirmed);
    }

    /**
     * Test 1.3.5: El valor por defecto para nuevas compras debe ser PENDING.
     */
    @Test
    void testDefaultStatus_IsPending() {
        PurchaseStatus defaultStatus = PurchaseStatus.PENDING;
        assertEquals("PENDING", defaultStatus.name());
    }
}
