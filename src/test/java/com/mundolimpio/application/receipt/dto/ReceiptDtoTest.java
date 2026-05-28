package com.mundolimpio.application.receipt.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para los DTOs del módulo receipt.
 * 
 * POR QUÉ tests unitarios puros:
 * - Los DTOs son Java Records (inmutables, sin lógica de negocio).
 * - No necesitan Spring context ni base de datos.
 * - Verificamos que se construyen correctamente y los campos son accesibles.
 */
class ReceiptDtoTest {

    // ==================== ProductLineDto ====================

    /**
     * Test 1.5.1 RED: ProductLineDto debe construirse con todos los campos.
     * Triangulamos con dos DTOs: uno con bulkProductId y otro sin.
     */
    @Test
    void testProductLineDto_Construction() {
        ProductLineDto dto1 = new ProductLineDto(
                "Cloro 5L", 3, new BigDecimal("150.00"), 0.95, 10L);
        ProductLineDto dto2 = new ProductLineDto(
                "Desconocido", 1, new BigDecimal("50.00"), 0.25, null);

        assertEquals("Cloro 5L", dto1.name());
        assertEquals(3, dto1.quantity());
        assertEquals(0, new BigDecimal("150.00").compareTo(dto1.unitPrice()));
        assertEquals(0.95, dto1.confidence(), 0.001);
        assertEquals(10L, dto1.bulkProductId());

        assertEquals("Desconocido", dto2.name());
        assertNull(dto2.bulkProductId());
    }

    // ==================== ReceiptProcessResponse ====================

    /**
     * Test 1.5.2 RED: ReceiptProcessResponse debe construirse con supplier, date, lines e imageUrl.
     */
    @Test
    void testReceiptProcessResponse_Construction() {
        List<ProductLineDto> lines = List.of(
                new ProductLineDto("Item A", 1, BigDecimal.TEN, 0.80, 1L),
                new ProductLineDto("Item B", 2, BigDecimal.ONE, 0.90, null)
        );

        ReceiptProcessResponse response = new ReceiptProcessResponse(
                "Proveedor XYZ", "2026-05-15", lines,
                "https://storage.example.com/receipt1.jpg");

        assertEquals("Proveedor XYZ", response.detectedSupplier());
        assertEquals("2026-05-15", response.detectedDate());
        assertEquals(2, response.lines().size());
        assertEquals("https://storage.example.com/receipt1.jpg", response.imageUrl());
    }

    // ==================== ProductLineConfirmDto ====================

    /**
     * Test 1.5.3 RED: ProductLineConfirmDto debe construirse con todos los campos.
     * Triangulamos: con y sin bulkProductId.
     */
    @Test
    void testProductLineConfirmDto_Construction() {
        ProductLineConfirmDto dto1 = new ProductLineConfirmDto(
                "Cloro concentrado 5L", 3, new BigDecimal("150.00"), 10L);
        ProductLineConfirmDto dto2 = new ProductLineConfirmDto(
                "Producto nuevo", 1, new BigDecimal("99.99"), null);

        assertEquals("Cloro concentrado 5L", dto1.description());
        assertEquals(3, dto1.quantity());
        assertEquals(0, new BigDecimal("150.00").compareTo(dto1.unitPrice()));
        assertEquals(10L, dto1.bulkProductId());

        assertNull(dto2.bulkProductId());
    }

    // ==================== ReceiptConfirmRequest ====================

    /**
     * Test 1.5.4 RED: ReceiptConfirmRequest debe construirse con supplierName, date, lines, imageUrl.
     * Triangulamos con 1 línea y con múltiples líneas.
     */
    @Test
    void testReceiptConfirmRequest_Construction() {
        List<ProductLineConfirmDto> singleLine = List.of(
                new ProductLineConfirmDto("Cloro", 2, new BigDecimal("100.00"), 1L));

        List<ProductLineConfirmDto> multiLines = List.of(
                new ProductLineConfirmDto("A", 1, BigDecimal.TEN, null),
                new ProductLineConfirmDto("B", 2, BigDecimal.ONE, 5L));

        ReceiptConfirmRequest req1 = new ReceiptConfirmRequest(
                "https://img.jpg", "Proveedor A", LocalDate.of(2026, 5, 10), singleLine);
        ReceiptConfirmRequest req2 = new ReceiptConfirmRequest(
                "https://img2.jpg", "Proveedor B", LocalDate.of(2026, 5, 15), multiLines);

        assertEquals("Proveedor A", req1.supplierName());
        assertEquals(1, req1.lines().size());
        assertEquals(LocalDate.of(2026, 5, 10), req1.purchaseDate());

        assertEquals("Proveedor B", req2.supplierName());
        assertEquals(2, req2.lines().size());
    }

    // ==================== PurchaseItemResponse ====================

    /**
     * Test 1.5.5 RED: PurchaseItemResponse debe construirse con todos los campos.
     */
    @Test
    void testPurchaseItemResponse_Construction() {
        PurchaseItemResponse item1 = new PurchaseItemResponse(
                100L, "Cloro 5L", 3,
                new BigDecimal("150.00"), new BigDecimal("450.00"), 10L);
        PurchaseItemResponse item2 = new PurchaseItemResponse(
                101L, "Desconocido", 1,
                new BigDecimal("50.00"), new BigDecimal("50.00"), null);

        assertEquals(100L, item1.id());
        assertEquals("Cloro 5L", item1.description());
        assertEquals(3, item1.quantity());
        assertEquals(0, new BigDecimal("150.00").compareTo(item1.unitPrice()));
        assertEquals(0, new BigDecimal("450.00").compareTo(item1.totalPrice()));
        assertEquals(10L, item1.bulkProductId());

        assertNull(item2.bulkProductId());
    }

    // ==================== PurchaseResponse ====================

    /**
     * Test 1.5.6 RED: PurchaseResponse debe construirse con id, imageUrl, supplierName, date, total, items.
     */
    @Test
    void testPurchaseResponse_Construction() {
        List<PurchaseItemResponse> items = List.of(
                new PurchaseItemResponse(1L, "Item A", 2,
                        BigDecimal.TEN, new BigDecimal("20.00"), 1L));

        PurchaseResponse response = new PurchaseResponse(
                500L, "https://img.jpg", "Proveedor XYZ",
                LocalDate.of(2026, 5, 15), new BigDecimal("20.00"), items);

        assertEquals(500L, response.id());
        assertEquals("https://img.jpg", response.imageUrl());
        assertEquals("Proveedor XYZ", response.supplierName());
        assertEquals(LocalDate.of(2026, 5, 15), response.purchaseDate());
        assertEquals(0, new BigDecimal("20.00").compareTo(response.total()));
        assertEquals(1, response.items().size());
    }
}
