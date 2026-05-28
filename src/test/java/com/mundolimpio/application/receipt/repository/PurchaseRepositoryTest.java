package com.mundolimpio.application.receipt.repository;

import com.mundolimpio.application.receipt.domain.*;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración para PurchaseRepository y PurchaseItemRepository.
 * 
 * POR QUÉ AbstractIntegrationTest:
 * - Verificamos que JPA persiste correctamente las relaciones @ManyToOne y @OneToMany.
 * - Cascade ALL + orphanRemoval deben funcionar contra PostgreSQL real.
 * 
 * PENDING: Requiere Docker corriendo para Testcontainers.
 */
class PurchaseRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PurchaseItemRepository purchaseItemRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private UserRepository userRepository;

    private Supplier supplier;
    private User admin;

    @BeforeEach
    void setUp() {
        purchaseItemRepository.deleteAll();
        purchaseRepository.deleteAll();
        supplierRepository.deleteAll();
        userRepository.deleteAll();

        supplier = supplierRepository.save(new Supplier("Proveedor Test"));
        admin = userRepository.save(new User("admin_repo", "admin_repo@mundolimpio.com", "pass", Role.ADMIN));
    }

    /**
     * Test 1.4.3 RED: Save + findById con cascade de items.
     * Triangulación: verificamos que la compra y sus items se persisten juntos.
     */
    @Test
    @Transactional
    void testSaveAndFind_PurchaseWithItems() {
        // Given
        Purchase purchase = new Purchase(
                "https://storage.example.com/img.jpg",
                supplier, admin,
                LocalDate.of(2026, 5, 15),
                new BigDecimal("300.00"),
                PurchaseStatus.PENDING);

        purchase.addItem(new PurchaseItem("Cloro 5L", 2,
                new BigDecimal("150.00"), new BigDecimal("300.00"), null));

        // When
        Purchase saved = purchaseRepository.save(purchase);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getDescription()).isEqualTo("Cloro 5L");

        // Verificar que el PurchaseItem también se persistió
        Purchase found = purchaseRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getImageUrl()).isEqualTo("https://storage.example.com/img.jpg");
        assertThat(found.getSupplier().getName()).isEqualTo("Proveedor Test");
        assertThat(found.getStatus()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(found.getTotal()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    /**
     * Test 1.4.4: PurchaseItem con bulkProductId null (producto no matcheado).
     * Verifica que el campo nullable funciona correctamente.
     */
    @Test
    void testSave_PurchaseItem_WithNullBulkProductId() {
        Purchase purchase = new Purchase(
                "https://img.jpg", supplier, admin,
                LocalDate.now(), new BigDecimal("50.00"), PurchaseStatus.PENDING);

        purchase.addItem(new PurchaseItem("Producto Desconocido", 1,
                new BigDecimal("50.00"), new BigDecimal("50.00"), null));

        Purchase saved = purchaseRepository.save(purchase);

        assertThat(saved.getItems().get(0).getBulkProductId()).isNull();
    }
}
