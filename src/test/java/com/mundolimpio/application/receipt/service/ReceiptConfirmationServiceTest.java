package com.mundolimpio.application.receipt.service;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.receipt.domain.Purchase;
import com.mundolimpio.application.receipt.domain.PurchaseItem;
import com.mundolimpio.application.receipt.domain.Supplier;
import com.mundolimpio.application.receipt.dto.*;
import com.mundolimpio.application.receipt.mapper.ReceiptMapper;
import com.mundolimpio.application.receipt.repository.PurchaseRepository;
import com.mundolimpio.application.receipt.repository.SupplierRepository;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WHAT: Tests unitarios para ReceiptConfirmationService.
 * WHY: Verifica la orquestación del flujo de confirmación:
 *      find-or-create Supplier → mapear request a Purchase (CONFIRMED) →
 *      persistir vía PurchaseRepository → actualizar stock BulkProduct →
 *      retornar PurchaseResponse.
 *
 * DIFFERENCES con ReceiptProcessingServiceTest:
 * - Este test mockea repositorios JPA (SupplierRepository, PurchaseRepository,
 *   BulkProductRepository) en vez de servicios externos.
 * - Verifica interacciones con la DB (save, findById, findByName).
 */
@ExtendWith(MockitoExtension.class)
class ReceiptConfirmationServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private BulkProductRepository bulkProductRepository;

    @Mock
    private ReceiptMapper mapper;

    @InjectMocks
    private ReceiptConfirmationService confirmationService;

    private User admin;
    private Supplier existingSupplier;
    private ReceiptConfirmRequest validRequest;
    private Purchase realPurchase;
    private PurchaseResponse mockResponse;

    @BeforeEach
    void setUp() {
        admin = new User("admin_test", "admin_test@mundolimpio.com", "pass", Role.ADMIN);
        existingSupplier = new Supplier("Proveedor Existente");

        validRequest = new ReceiptConfirmRequest(
                "https://storage.example.com/receipts/img.jpg",
                "Proveedor Existente",
                LocalDate.of(2026, 5, 15),
                List.of(
                        new ProductLineConfirmDto("Cloro 5L", 2,
                                new BigDecimal("150.00"), 10L),
                        new ProductLineConfirmDto("Detergente", 1,
                                new BigDecimal("200.00"), null)
                )
        );

        // Purchase real (no mock) con items concretos
        realPurchase = new Purchase(
                "https://storage.example.com/receipts/img.jpg",
                existingSupplier,
                admin,
                LocalDate.of(2026, 5, 15),
                new BigDecimal("500.00"),
                com.mundolimpio.application.receipt.domain.PurchaseStatus.CONFIRMED
        );
        realPurchase.addItem(new PurchaseItem("Cloro 5L", 2,
                new BigDecimal("150.00"), new BigDecimal("300.00"), 10L));
        realPurchase.addItem(new PurchaseItem("Detergente", 1,
                new BigDecimal("200.00"), new BigDecimal("200.00"), null));

        mockResponse = new PurchaseResponse(
                1L,
                "https://storage.example.com/receipts/img.jpg",
                "Proveedor Existente",
                LocalDate.of(2026, 5, 15),
                new BigDecimal("500.00"),
                List.of(
                        new PurchaseItemResponse(1L, "Cloro 5L", 2,
                                new BigDecimal("150.00"), new BigDecimal("300.00"), 10L),
                        new PurchaseItemResponse(2L, "Detergente", 1,
                                new BigDecimal("200.00"), new BigDecimal("200.00"), null)
                )
        );
    }

    // ===================== RED: Tests de confirmación exitosa =====================

    /**
     * RED: Verifica que confirm() retorna PurchaseResponse cuando el proveedor ya existe.
     * TRIANGULATE: Caso con supplier existente + items con y sin bulkProductId.
     */
    @Test
    void shouldConfirmWhenSupplierExists() {
        when(supplierRepository.findByName("Proveedor Existente"))
                .thenReturn(Optional.of(existingSupplier));
        when(mapper.toEntity(validRequest, existingSupplier, admin))
                .thenReturn(realPurchase);
        when(purchaseRepository.save(realPurchase)).thenReturn(realPurchase);
        when(mapper.toResponse(realPurchase)).thenReturn(mockResponse);

        // Mock del stock update: BulkProduct 10L existe
        BulkProduct bulkProduct = new BulkProduct(10L, "Cloro Puro",
                new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("4.00"));
        when(bulkProductRepository.findById(10L))
                .thenReturn(Optional.of(bulkProduct));
        when(bulkProductRepository.save(any(BulkProduct.class)))
                .thenReturn(bulkProduct);

        PurchaseResponse response = confirmationService.confirm(validRequest, admin);

        assertThat(response).isNotNull();
        assertThat(response.supplierName()).isEqualTo("Proveedor Existente");
        assertThat(response.total()).isEqualByComparingTo("500.00");
        assertThat(response.items()).hasSize(2);

        // Verificar que NO se creó un nuevo Supplier (ya existía)
        verify(supplierRepository, never()).save(any(Supplier.class));

        // Verificar que Purchase se guardó
        verify(purchaseRepository).save(realPurchase);

        // Verificar que el stock se actualizó para el item con bulkProductId
        verify(bulkProductRepository).findById(10L);
        verify(bulkProductRepository).save(any(BulkProduct.class));
    }

    /**
     * RED: Verifica find-or-create de Supplier cuando no existe.
     * TRIANGULATE: Caso con supplier nuevo (no se encuentra por nombre).
     */
    @Test
    void shouldCreateSupplierWhenNotFound() {
        Supplier newSupplier = new Supplier("Nuevo Proveedor");

        ReceiptConfirmRequest requestNewSupplier = new ReceiptConfirmRequest(
                "https://storage.example.com/receipts/img.jpg",
                "Nuevo Proveedor",
                LocalDate.of(2026, 5, 15),
                List.of(new ProductLineConfirmDto("Producto X", 1,
                        new BigDecimal("100.00"), null))
        );

        Purchase singleItemPurchase = new Purchase(
                "https://storage.example.com/receipts/img.jpg",
                newSupplier,
                admin,
                LocalDate.of(2026, 5, 15),
                new BigDecimal("100.00"),
                com.mundolimpio.application.receipt.domain.PurchaseStatus.CONFIRMED
        );
        singleItemPurchase.addItem(new PurchaseItem("Producto X", 1,
                new BigDecimal("100.00"), new BigDecimal("100.00"), null));

        PurchaseResponse singleResponse = new PurchaseResponse(
                2L, "https://storage.example.com/receipts/img.jpg",
                "Nuevo Proveedor", LocalDate.of(2026, 5, 15),
                new BigDecimal("100.00"),
                List.of(new PurchaseItemResponse(3L, "Producto X", 1,
                        new BigDecimal("100.00"), new BigDecimal("100.00"), null))
        );

        when(supplierRepository.findByName("Nuevo Proveedor"))
                .thenReturn(Optional.empty());
        when(supplierRepository.save(any(Supplier.class)))
                .thenReturn(newSupplier);
        when(mapper.toEntity(requestNewSupplier, newSupplier, admin))
                .thenReturn(singleItemPurchase);
        when(purchaseRepository.save(singleItemPurchase))
                .thenReturn(singleItemPurchase);
        when(mapper.toResponse(singleItemPurchase))
                .thenReturn(singleResponse);

        PurchaseResponse response = confirmationService.confirm(requestNewSupplier, admin);

        assertThat(response).isNotNull();
        assertThat(response.supplierName()).isEqualTo("Nuevo Proveedor");

        // Verificar que se creó un nuevo Supplier
        ArgumentCaptor<Supplier> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository).save(supplierCaptor.capture());
        assertThat(supplierCaptor.getValue().getName()).isEqualTo("Nuevo Proveedor");
    }

    // ===================== RED: Tests de actualización de stock =====================

    /**
     * RED: Verifica que el stock se incrementa para items con bulkProductId.
     * TRIANGULATE: Verifica el valor exacto del incremento.
     */
    @Test
    void shouldIncrementBulkProductStock() {
        when(supplierRepository.findByName("Proveedor Existente"))
                .thenReturn(Optional.of(existingSupplier));
        when(mapper.toEntity(validRequest, existingSupplier, admin))
                .thenReturn(realPurchase);
        when(purchaseRepository.save(realPurchase)).thenReturn(realPurchase);
        when(mapper.toResponse(realPurchase)).thenReturn(mockResponse);

        // BulkProduct con stock inicial 100.00 litros
        BulkProduct bulkProduct = new BulkProduct(10L, "Cloro Puro",
                new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("4.00"));
        when(bulkProductRepository.findById(10L))
                .thenReturn(Optional.of(bulkProduct));

        ArgumentCaptor<BulkProduct> bpCaptor = ArgumentCaptor.forClass(BulkProduct.class);
        when(bulkProductRepository.save(any(BulkProduct.class)))
                .thenReturn(bulkProduct);

        confirmationService.confirm(validRequest, admin);

        // Verificar que el stock se incrementó en la cantidad comprada (2 unidades)
        verify(bulkProductRepository).save(bpCaptor.capture());
        BigDecimal expectedStock = new BigDecimal("100.00").add(new BigDecimal("2"));
        assertThat(bpCaptor.getValue().getCurrentStockLiters())
                .isEqualByComparingTo(expectedStock);
    }

    /**
     * RED: Verifica que NO se actualiza stock para items sin bulkProductId (null).
     */
    @Test
    void shouldNotUpdateStockWhenBulkProductIdIsNull() {
        ReceiptConfirmRequest requestNoMatch = new ReceiptConfirmRequest(
                "https://storage.example.com/receipts/img.jpg",
                "Proveedor Existente",
                LocalDate.of(2026, 5, 15),
                List.of(new ProductLineConfirmDto("Producto Desconocido", 1,
                        new BigDecimal("50.00"), null))
        );

        Purchase purchaseNoMatch = new Purchase(
                "https://storage.example.com/receipts/img.jpg",
                existingSupplier,
                admin,
                LocalDate.of(2026, 5, 15),
                new BigDecimal("50.00"),
                com.mundolimpio.application.receipt.domain.PurchaseStatus.CONFIRMED
        );
        purchaseNoMatch.addItem(new PurchaseItem("Producto Desconocido", 1,
                new BigDecimal("50.00"), new BigDecimal("50.00"), null));

        PurchaseResponse responseNoMatch = new PurchaseResponse(
                3L, "https://storage.example.com/receipts/img.jpg",
                "Proveedor Existente", LocalDate.of(2026, 5, 15),
                new BigDecimal("50.00"),
                List.of(new PurchaseItemResponse(4L, "Producto Desconocido", 1,
                        new BigDecimal("50.00"), new BigDecimal("50.00"), null))
        );

        when(supplierRepository.findByName("Proveedor Existente"))
                .thenReturn(Optional.of(existingSupplier));
        when(mapper.toEntity(requestNoMatch, existingSupplier, admin))
                .thenReturn(purchaseNoMatch);
        when(purchaseRepository.save(purchaseNoMatch)).thenReturn(purchaseNoMatch);
        when(mapper.toResponse(purchaseNoMatch)).thenReturn(responseNoMatch);

        PurchaseResponse response = confirmationService.confirm(requestNoMatch, admin);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);

        // Verificar que NUNCA se buscó ni actualizó BulkProduct
        verify(bulkProductRepository, never()).findById(any());
        verify(bulkProductRepository, never()).save(any(BulkProduct.class));
    }

    /**
     * RED: Verifica que no falla si el BulkProduct no existe (log warning).
     * TRIANGULATE: Caso donde el bulkProductId referencia un ID inexistente.
     */
    @Test
    void shouldNotFailWhenBulkProductNotFound() {
        when(supplierRepository.findByName("Proveedor Existente"))
                .thenReturn(Optional.of(existingSupplier));
        when(mapper.toEntity(validRequest, existingSupplier, admin))
                .thenReturn(realPurchase);
        when(purchaseRepository.save(realPurchase)).thenReturn(realPurchase);
        when(mapper.toResponse(realPurchase)).thenReturn(mockResponse);

        // BulkProduct 10L NO existe
        when(bulkProductRepository.findById(10L))
                .thenReturn(Optional.empty());

        // No debe lanzar excepción
        PurchaseResponse response = confirmationService.confirm(validRequest, admin);

        assertThat(response).isNotNull();
        assertThat(response.supplierName()).isEqualTo("Proveedor Existente");

        // Verificar que se buscó el BulkProduct pero NO se guardó
        verify(bulkProductRepository).findById(10L);
        verify(bulkProductRepository, never()).save(any(BulkProduct.class));
    }

    // ===================== RED: Tests de cálculo de total =====================

    /**
     * RED: Verifica que el total se calcula como suma de los totalPrice de los items.
     */
    @Test
    void shouldCalculateTotalFromItems() {
        ReceiptConfirmRequest multiItemRequest = new ReceiptConfirmRequest(
                "https://storage.example.com/receipts/img.jpg",
                "Proveedor Existente",
                LocalDate.of(2026, 5, 15),
                List.of(
                        new ProductLineConfirmDto("Item A", 2,
                                new BigDecimal("100.00"), null),
                        new ProductLineConfirmDto("Item B", 3,
                                new BigDecimal("50.00"), 5L)
                )
        );

        Purchase multiItemPurchase = new Purchase(
                "https://storage.example.com/receipts/img.jpg",
                existingSupplier,
                admin,
                LocalDate.of(2026, 5, 15),
                new BigDecimal("350.00"),
                com.mundolimpio.application.receipt.domain.PurchaseStatus.CONFIRMED
        );
        multiItemPurchase.addItem(new PurchaseItem("Item A", 2,
                new BigDecimal("100.00"), new BigDecimal("200.00"), null));
        multiItemPurchase.addItem(new PurchaseItem("Item B", 3,
                new BigDecimal("50.00"), new BigDecimal("150.00"), 5L));

        PurchaseResponse multiResponse = new PurchaseResponse(
                5L, "https://storage.example.com/receipts/img.jpg",
                "Proveedor Existente", LocalDate.of(2026, 5, 15),
                new BigDecimal("350.00"),
                List.of(
                        new PurchaseItemResponse(6L, "Item A", 2,
                                new BigDecimal("100.00"), new BigDecimal("200.00"), null),
                        new PurchaseItemResponse(7L, "Item B", 3,
                                new BigDecimal("50.00"), new BigDecimal("150.00"), 5L)
                )
        );

        when(supplierRepository.findByName("Proveedor Existente"))
                .thenReturn(Optional.of(existingSupplier));
        when(mapper.toEntity(multiItemRequest, existingSupplier, admin))
                .thenReturn(multiItemPurchase);
        when(purchaseRepository.save(multiItemPurchase)).thenReturn(multiItemPurchase);
        when(mapper.toResponse(multiItemPurchase)).thenReturn(multiResponse);

        // BulkProduct 5L existe
        BulkProduct bp = new BulkProduct(5L, "Item B Bulk",
                new BigDecimal("50.00"), new BigDecimal("25.00"), new BigDecimal("1.00"));
        when(bulkProductRepository.findById(5L)).thenReturn(Optional.of(bp));
        when(bulkProductRepository.save(any(BulkProduct.class))).thenReturn(bp);

        PurchaseResponse response = confirmationService.confirm(multiItemRequest, admin);

        assertThat(response.total()).isEqualByComparingTo("350.00");
        assertThat(response.items()).hasSize(2);
    }
}
