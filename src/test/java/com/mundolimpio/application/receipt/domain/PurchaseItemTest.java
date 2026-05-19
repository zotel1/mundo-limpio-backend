package com.mundolimpio.application.receipt.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para la entidad PurchaseItem.
 * 
 * POR QUÉ tests unitarios puros:
 * - PurchaseItem es una entidad JPA con constructor y getters simples.
 * - No requiere Spring context ni base de datos para probar su comportamiento.
 */
class PurchaseItemTest {

    /**
     * Test 1.3.6 RED: Constructor debe setear todos los campos correctamente.
     * Triangulamos con dos items distintos para forzar lógica real.
     */
    @Test
    void testConstructor_SetsAllFields() {
        // Given
        String desc1 = "Cloro 5L";
        int qty1 = 3;
        BigDecimal price1 = new BigDecimal("150.50");
        BigDecimal total1 = new BigDecimal("451.50");

        String desc2 = "Detergente 20L";
        int qty2 = 1;
        BigDecimal price2 = new BigDecimal("850.00");
        BigDecimal total2 = new BigDecimal("850.00");

        // When
        PurchaseItem item1 = new PurchaseItem(desc1, qty1, price1, total1, 10L);
        PurchaseItem item2 = new PurchaseItem(desc2, qty2, price2, total2, null);

        // Then — item1
        assertEquals(desc1, item1.getDescription());
        assertEquals(qty1, item1.getQuantity());
        assertEquals(0, price1.compareTo(item1.getUnitPrice()));
        assertEquals(0, total1.compareTo(item1.getTotalPrice()));
        assertEquals(10L, item1.getBulkProductId());

        // Then — item2 (sin bulkProductId)
        assertEquals(desc2, item2.getDescription());
        assertEquals(qty2, item2.getQuantity());
        assertEquals(0, price2.compareTo(item2.getUnitPrice()));
        assertEquals(0, total2.compareTo(item2.getTotalPrice()));
        assertNull(item2.getBulkProductId());
    }

    /**
     * Test 1.3.7: bulkProductId debe ser nullable (producto no matcheado).
     */
    @Test
    void testBulkProductId_CanBeNull_ForUnmatchedProducts() {
        PurchaseItem item = new PurchaseItem("Producto desconocido", 1,
                new BigDecimal("100.00"), new BigDecimal("100.00"), null);

        assertNull(item.getBulkProductId());
        assertEquals("Producto desconocido", item.getDescription());
        assertEquals(1, item.getQuantity());
    }

    /**
     * Test 1.3.8: El ID debe ser null hasta que JPA lo persista.
     */
    @Test
    void testId_IsNullBeforePersistence() {
        PurchaseItem item = new PurchaseItem("Test", 1,
                BigDecimal.ONE, BigDecimal.ONE, null);
        assertNull(item.getId());
    }

    /**
     * Test 1.3.9: setPurchase debe establecer la relación bidireccional.
     */
    @Test
    void testSetPurchase_EstablishesRelationship() {
        // Given: un Purchase mínimo (necesitamos solo para la relación)
        Purchase purchase = new Purchase();
        PurchaseItem item = new PurchaseItem("Test", 1,
                BigDecimal.ONE, BigDecimal.ONE, null);

        // When
        item.setPurchase(purchase);

        // Then
        assertNotNull(item.getPurchase());
        assertSame(purchase, item.getPurchase());
    }
}
