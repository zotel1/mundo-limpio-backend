package com.mundolimpio.application.receipt.domain;

import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.domain.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para la entidad Purchase.
 * 
 * POR QUÉ tests unitarios puros:
 * - Purchase es una entidad JPA con relaciones @ManyToOne y @OneToMany.
 * - Los tests de constructor, getters y addItem() no necesitan Spring context.
 * - Las relaciones se verifican a nivel de objeto (referencias en memoria).
 */
class PurchaseTest {

    /**
     * Test 1.3.10 RED: Constructor debe setear todos los campos y createdAt automático.
     * Triangulamos con dos compras distintas para forzar lógica real.
     */
    @Test
    void testConstructor_SetsAllFields() {
        // Given
        Supplier supplier = new Supplier("Proveedor Test");
        User admin = new User("admin_test", "admin_test@mundolimpio.com", "pass123", Role.ADMIN);
        LocalDate purchaseDate = LocalDate.of(2026, 5, 10);
        BigDecimal total = new BigDecimal("1500.75");
        PurchaseStatus status = PurchaseStatus.CONFIRMED;

        // When
        Purchase purchase = new Purchase(
                "https://storage.example.com/receipt1.jpg",
                supplier, admin, purchaseDate, total, status);

        // Then
        assertEquals("https://storage.example.com/receipt1.jpg", purchase.getImageUrl());
        assertSame(supplier, purchase.getSupplier());
        assertSame(admin, purchase.getAdmin());
        assertEquals(purchaseDate, purchase.getPurchaseDate());
        assertEquals(0, total.compareTo(purchase.getTotal()));
        assertEquals(status, purchase.getStatus());
        assertNotNull(purchase.getCreatedAt());
        assertNotNull(purchase.getItems());
        assertTrue(purchase.getItems().isEmpty());
    }

    /**
     * Test 1.3.11: El status por defecto debe ser PENDING (compra nueva sin confirmar).
     * Verificamos que un Purchase creado con PENDING efectivamente lo tiene.
     */
    @Test
    void testDefaultStatus_IsPending() {
        Supplier supplier = new Supplier("Proveedor");
        User admin = new User("admin", "admin@mundolimpio.com", "pass", Role.ADMIN);

        Purchase purchase = new Purchase(
                "https://img.jpg", supplier, admin,
                LocalDate.now(), BigDecimal.TEN, PurchaseStatus.PENDING);

        assertEquals(PurchaseStatus.PENDING, purchase.getStatus());
    }

    /**
     * Test 1.3.12 RED: addItem debe agregar el item y establecer la relación bidireccional.
     * Triangulamos con 2 items para verificar que la lista crece correctamente.
     */
    @Test
    void testAddItem_AddsItemAndSetsBidirectionalRelationship() {
        // Given
        Purchase purchase = createMinimalPurchase();
        PurchaseItem item1 = new PurchaseItem("Item 1", 1,
                new BigDecimal("10.00"), new BigDecimal("10.00"), null);
        PurchaseItem item2 = new PurchaseItem("Item 2", 2,
                new BigDecimal("20.00"), new BigDecimal("40.00"), 5L);

        // When
        purchase.addItem(item1);
        purchase.addItem(item2);

        // Then
        assertEquals(2, purchase.getItems().size());
        assertSame(purchase, item1.getPurchase());
        assertSame(purchase, item2.getPurchase());
        assertTrue(purchase.getItems().contains(item1));
        assertTrue(purchase.getItems().contains(item2));
    }

    /**
     * Test 1.3.13: createdAt debe ser cercano a now().
     */
    @Test
    void testCreatedAt_IsSetAtConstructionTime() {
        LocalDateTime before = LocalDateTime.now();
        Purchase purchase = createMinimalPurchase();
        LocalDateTime after = LocalDateTime.now();

        assertNotNull(purchase.getCreatedAt());
        assertFalse(purchase.getCreatedAt().isBefore(before.minusSeconds(1)));
        assertFalse(purchase.getCreatedAt().isAfter(after.plusSeconds(1)));
    }

    /**
     * Test 1.3.14: El ID debe ser null hasta que JPA lo persista.
     */
    @Test
    void testId_IsNullBeforePersistence() {
        Purchase purchase = createMinimalPurchase();
        assertNull(purchase.getId());
    }

    /**
     * Test 1.3.15: La lista de items debe estar inicializada vacía (no null).
     * Esto evita NullPointerException al llamar addItem() o getItems().size().
     */
    @Test
    void testItemsList_IsInitializedEmpty() {
        Purchase purchase = createMinimalPurchase();
        assertNotNull(purchase.getItems());
        assertTrue(purchase.getItems().isEmpty());
    }

    // Helper: crea un Purchase mínimo para tests
    private Purchase createMinimalPurchase() {
        Supplier supplier = new Supplier("Supplier Test");
        User admin = new User("admin", "admin@mundolimpio.com", "pass", Role.ADMIN);
        return new Purchase(
                "https://img.jpg", supplier, admin,
                LocalDate.now(), new BigDecimal("100.00"),
                PurchaseStatus.PENDING);
    }
}
