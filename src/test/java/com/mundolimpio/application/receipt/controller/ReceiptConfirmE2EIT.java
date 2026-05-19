package com.mundolimpio.application.receipt.controller;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.receipt.domain.Purchase;
import com.mundolimpio.application.receipt.domain.PurchaseStatus;
import com.mundolimpio.application.receipt.domain.Supplier;
import com.mundolimpio.application.receipt.repository.PurchaseRepository;
import com.mundolimpio.application.receipt.repository.SupplierRepository;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WHAT: Test end-to-end del flujo completo de confirmación de compra.
 * WHY: Verifica que el flujo real (sin mocks de servicios) funciona correctamente:
 *      POST /confirm → Purchase + PurchaseItems persistidos → Supplier creado →
 *      stock de BulkProduct actualizado.
 * 
 * DIFFERENCES con ReceiptControllerIT:
 * - Este test NO mockea ReceiptConfirmationService ni ReceiptProcessingService.
 * - Usa los servicios reales con la DB de Testcontainers (PostgreSQL real).
 * - Solo mockea dependencias externas (ITesseract, S3Client) via AbstractIntegrationTest.
 * - Verifica el estado final de la base de datos (entidades persistidas).
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ReceiptConfirmE2EIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private BulkProductRepository bulkProductRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        supplierRepository.deleteAll();
        purchaseRepository.deleteAll();
        bulkProductRepository.deleteAll();

        // Crear admin y generar JWT
        User admin = new User("admin_e2e", "$2a$10$encodedPassword", Role.ADMIN);
        userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);
    }

    /**
     * RED: Flujo E2E completo — confirmar compra con Supplier existente y BulkProduct.
     * 
     * TRIANGULATE:
     * - 3 items: 2 con bulkProductId, 1 sin
     * - Supplier ya existe en DB (creado previamente)
     * - BulkProduct ya existe en DB con stock inicial
     * - Verificar: Purchase, PurchaseItems, Supplier (no duplicado), stock incrementado
     */
    @Test
    void shouldCompleteFullConfirmFlow() throws Exception {
        // Precondición: crear Supplier existente
        Supplier existingSupplier = new Supplier("Distribuidora ABC");
        supplierRepository.save(existingSupplier);

        // Precondición: crear BulkProduct con stock inicial
        BulkProduct cloroBulk = new BulkProduct(null, "Cloro Puro",
                new BigDecimal("50.00"), new BigDecimal("120.00"), new BigDecimal("4.00"));
        BulkProduct detergenteBulk = new BulkProduct(null, "Detergente Base",
                new BigDecimal("30.00"), new BigDecimal("80.00"), new BigDecimal("3.00"));
        bulkProductRepository.saveAll(List.of(cloroBulk, detergenteBulk));

        // Request de confirmación con 3 items
        String requestBody = """
                {
                    "imageUrl": "https://storage.example.com/receipts/ticket-001.jpg",
                    "supplierName": "Distribuidora ABC",
                    "purchaseDate": "2026-05-19",
                    "lines": [
                        {
                            "description": "Cloro Concentrado 5L",
                            "quantity": 3,
                            "unitPrice": 150.00,
                            "bulkProductId": %d
                        },
                        {
                            "description": "Detergente Líquido 3L",
                            "quantity": 5,
                            "unitPrice": 90.00,
                            "bulkProductId": %d
                        },
                        {
                            "description": "Escobillón Premium",
                            "quantity": 2,
                            "unitPrice": 250.00,
                            "bulkProductId": null
                        }
                    ]
                }
                """.formatted(cloroBulk.getId(), detergenteBulk.getId());

        // WHEN: POST /api/v1/receipts/confirm
        mockMvc.perform(post("/api/v1/receipts/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + adminToken))
                // THEN: 201 CREATED con todos los campos
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.imageUrl").value("https://storage.example.com/receipts/ticket-001.jpg"))
                .andExpect(jsonPath("$.supplierName").value("Distribuidora ABC"))
                .andExpect(jsonPath("$.purchaseDate").value("2026-05-19"))
                .andExpect(jsonPath("$.total").value(1400.00))  // 3*150 + 5*90 + 2*250 = 450+450+500 = 1400
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].description").value("Cloro Concentrado 5L"))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].bulkProductId").value(cloroBulk.getId()))
                .andExpect(jsonPath("$.items[2].bulkProductId").isEmpty());

        // VERIFICAR estado de la base de datos

        // 1. Supplier: NO se duplicó (solo 1 registro)
        List<Supplier> suppliers = supplierRepository.findAll();
        assertThat(suppliers).hasSize(1);
        assertThat(suppliers.get(0).getName()).isEqualTo("Distribuidora ABC");

        // 2. Purchase: persistida con status CONFIRMED
        List<Purchase> purchases = purchaseRepository.findAll();
        assertThat(purchases).hasSize(1);
        Purchase persistedPurchase = purchases.get(0);
        assertThat(persistedPurchase.getStatus()).isEqualTo(PurchaseStatus.CONFIRMED);
        assertThat(persistedPurchase.getImageUrl())
                .isEqualTo("https://storage.example.com/receipts/ticket-001.jpg");
        assertThat(persistedPurchase.getItems()).hasSize(3);
        // Total: 3*150 + 5*90 + 2*250 = 450 + 450 + 500 = 1400
        assertThat(persistedPurchase.getTotal()).isEqualByComparingTo(new BigDecimal("1400.00"));

        // 3. Stock de BulkProduct actualizado
        BulkProduct updatedCloro = bulkProductRepository.findById(cloroBulk.getId()).orElseThrow();
        assertThat(updatedCloro.getCurrentStockLiters())
                .isEqualByComparingTo(new BigDecimal("53.00")); // 50.00 + 3

        BulkProduct updatedDetergente = bulkProductRepository.findById(detergenteBulk.getId()).orElseThrow();
        assertThat(updatedDetergente.getCurrentStockLiters())
                .isEqualByComparingTo(new BigDecimal("35.00")); // 30.00 + 5
    }

    /**
     * RED: Flujo E2E — Supplier nuevo (find-or-create).
     * 
     * TRIANGULATE: Supplier no existe en DB, debe crearse automáticamente.
     */
    @Test
    void shouldCreateSupplierWhenNotFound() throws Exception {
        String requestBody = """
                {
                    "imageUrl": "https://storage.example.com/receipts/ticket-002.jpg",
                    "supplierName": "Nuevo Proveedor SRL",
                    "purchaseDate": "2026-05-19",
                    "lines": [
                        {
                            "description": "Producto Único",
                            "quantity": 10,
                            "unitPrice": 50.00,
                            "bulkProductId": null
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/receipts/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierName").value("Nuevo Proveedor SRL"))
                .andExpect(jsonPath("$.total").value(500.00))
                .andExpect(jsonPath("$.items.length()").value(1));

        // Verificar que el Supplier se creó
        List<Supplier> suppliers = supplierRepository.findAll();
        assertThat(suppliers).hasSize(1);
        assertThat(suppliers.get(0).getName()).isEqualTo("Nuevo Proveedor SRL");
    }

    /**
     * RED: Flujo E2E — item sin BulkProduct no afecta stock.
     * 
     * TRIANGULATE: Ningún item tiene bulkProductId → ningún stock se modifica.
     */
    @Test
    void shouldNotAffectStockWhenNoBulkProductMatch() throws Exception {
        // Crear BulkProduct con stock inicial (no debería cambiar)
        BulkProduct untouchedBulk = new BulkProduct(null, "Producto Sin Match",
                new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("1.00"));
        bulkProductRepository.save(untouchedBulk);

        // Crear Supplier para el request
        Supplier supplier = new Supplier("Proveedor Test");
        supplierRepository.save(supplier);

        String requestBody = """
                {
                    "imageUrl": "https://storage.example.com/receipts/ticket-003.jpg",
                    "supplierName": "Proveedor Test",
                    "purchaseDate": "2026-05-19",
                    "lines": [
                        {
                            "description": "Producto Sin Match",
                            "quantity": 5,
                            "unitPrice": 30.00,
                            "bulkProductId": null
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/receipts/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());

        // Verificar que el stock NO cambió
        BulkProduct stillUntouched = bulkProductRepository.findById(untouchedBulk.getId()).orElseThrow();
        assertThat(stillUntouched.getCurrentStockLiters())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
