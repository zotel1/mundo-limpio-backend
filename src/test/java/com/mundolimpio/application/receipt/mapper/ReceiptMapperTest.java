package com.mundolimpio.application.receipt.mapper;

import com.mundolimpio.application.receipt.domain.*;
import com.mundolimpio.application.receipt.dto.*;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para ReceiptMapper.
 * 
 * POR QUÉ tests unitarios puros (sin Spring context):
 * - El mapper es una clase de conversión de datos sin dependencias externas.
 * - No necesita @Autowired ni Spring context — podemos instanciarlo manualmente.
 * - Esto es MÁS rápido y MÁS puro que usar @SpringBootTest (como hace SaleMapperTest).
 * 
 * DIFFERENCES con SaleMapperTest:
 * - SaleMapperTest extiende AbstractIntegrationTest (necesita Docker).
 * - Este test es 100% autónomo — no necesita infraestructura externa.
 */
class ReceiptMapperTest {

    private ReceiptMapper mapper;
    private Supplier supplier;
    private User admin;

    @BeforeEach
    void setUp() {
        mapper = new ReceiptMapper();
        supplier = new Supplier("Proveedor Test");
        admin = new User("admin", "admin@mundolimpio.com", "pass", Role.ADMIN);
    }

    /**
     * Test 1.7.1 RED: toEntity debe convertir ReceiptConfirmRequest → Purchase.
     * Triangulamos con 1 línea y con múltiples líneas.
     */
    @Test
    void testToEntity_ConvertsRequestToPurchase() {
        // Given: request con una sola línea
        List<ProductLineConfirmDto> singleLine = List.of(
                new ProductLineConfirmDto("Cloro 5L", 3,
                        new BigDecimal("150.00"), 10L));

        ReceiptConfirmRequest request1 = new ReceiptConfirmRequest(
                "https://img.jpg", "Proveedor Test",
                LocalDate.of(2026, 5, 15), singleLine);

        // When
        Purchase purchase1 = mapper.toEntity(request1, supplier, admin);

        // Then
        assertEquals("https://img.jpg", purchase1.getImageUrl());
        assertSame(supplier, purchase1.getSupplier());
        assertSame(admin, purchase1.getAdmin());
        assertEquals(LocalDate.of(2026, 5, 15), purchase1.getPurchaseDate());
        assertEquals(PurchaseStatus.CONFIRMED, purchase1.getStatus());
        assertEquals(1, purchase1.getItems().size());

        PurchaseItem item1 = purchase1.getItems().get(0);
        assertEquals("Cloro 5L", item1.getDescription());
        assertEquals(3, item1.getQuantity());
        assertEquals(0, new BigDecimal("150.00").compareTo(item1.getUnitPrice()));
        assertEquals(10L, item1.getBulkProductId());
    }

    /**
     * Test 1.7.2: toEntity con múltiples líneas — todas deben convertirse.
     */
    @Test
    void testToEntity_MultipleLines() {
        List<ProductLineConfirmDto> multiLines = List.of(
                new ProductLineConfirmDto("Item A", 1, new BigDecimal("50.00"), null),
                new ProductLineConfirmDto("Item B", 2, new BigDecimal("25.00"), 5L));

        ReceiptConfirmRequest request = new ReceiptConfirmRequest(
                "https://img.jpg", "Proveedor Test",
                LocalDate.now(), multiLines);

        Purchase purchase = mapper.toEntity(request, supplier, admin);

        assertEquals(2, purchase.getItems().size());
        assertNull(purchase.getItems().get(0).getBulkProductId());
        assertEquals(5L, purchase.getItems().get(1).getBulkProductId());
    }

    /**
     * Test 1.7.3: toEntity debe calcular totalAmount como suma de los totalPrice de los items.
     */
    @Test
    void testToEntity_CalculatesTotalAmount() {
        List<ProductLineConfirmDto> lines = List.of(
                new ProductLineConfirmDto("A", 2, new BigDecimal("100.00"), null),
                new ProductLineConfirmDto("B", 1, new BigDecimal("200.00"), null));

        ReceiptConfirmRequest request = new ReceiptConfirmRequest(
                "https://img.jpg", "Proveedor",
                LocalDate.now(), lines);

        Purchase purchase = mapper.toEntity(request, supplier, admin);

        // totalPrice de cada item: 2*100 = 200, 1*200 = 200 → total = 400
        assertEquals(0, new BigDecimal("400.00").compareTo(purchase.getTotal()));
    }

    /**
     * Test 1.7.4 RED: toResponse debe convertir Purchase → PurchaseResponse.
     * Triangulamos verificando cada campo del response.
     */
    @Test
    void testToResponse_ConvertsPurchaseToResponse() {
        // Given
        Purchase purchase = new Purchase(
                "https://img.jpg", supplier, admin,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("300.00"),
                PurchaseStatus.CONFIRMED);

        purchase.addItem(new PurchaseItem("Cloro 5L", 2,
                new BigDecimal("150.00"), new BigDecimal("300.00"), 10L));

        // When
        PurchaseResponse response = mapper.toResponse(purchase);

        // Then
        assertEquals("https://img.jpg", response.imageUrl());
        assertEquals("Proveedor Test", response.supplierName());
        assertEquals(LocalDate.of(2026, 6, 1), response.purchaseDate());
        assertEquals(0, new BigDecimal("300.00").compareTo(response.total()));
        assertEquals(1, response.items().size());

        PurchaseItemResponse itemResp = response.items().get(0);
        assertEquals("Cloro 5L", itemResp.description());
        assertEquals(2, itemResp.quantity());
        assertEquals(0, new BigDecimal("150.00").compareTo(itemResp.unitPrice()));
        assertEquals(10L, itemResp.bulkProductId());
    }
}
