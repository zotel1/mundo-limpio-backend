package com.mundolimpio.application.security.config;

import com.mundolimpio.application.bulkproduct.dto.BulkProductResponse;
import com.mundolimpio.application.bulkproduct.service.BulkProductService;
import com.mundolimpio.application.inventory.dto.InventoryResponse;
import com.mundolimpio.application.inventory.service.InventoryService;
import com.mundolimpio.application.product.dto.ProductResponse;
import com.mundolimpio.application.product.service.ProductService;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchResponse;
import com.mundolimpio.application.productionbatch.service.ProductionBatchService;
import com.mundolimpio.application.receipt.dto.PurchaseResponse;
import com.mundolimpio.application.receipt.service.ReceiptConfirmationService;
import com.mundolimpio.application.receipt.service.ReceiptProcessingService;
import com.mundolimpio.application.sales.dto.SaleResponse;
import com.mundolimpio.application.sales.service.SaleService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WHAT: Test de seguridad RBAC que verifica la matriz de permisos de 6 roles.
 *
 * WHY: Tras expandir los @PreAuthorize de hasRole('ADMIN') a hasAnyRole(...)
 *      para cada endpoint según la matriz de permisos, estos tests validan
 *      que cada rol accede solo a los endpoints que le corresponden.
 *
 * MATRIZ VERIFICADA:
 * - ADMIN: acceso total a todos los endpoints
 * - STOCK_MANAGER: product (write), bulk (all), inventory (read/write), receipt (process), production (read)
 * - STOCK_OPERATOR: inventory (read), receipt (process)
 * - SALES_CLERK: sales (write), inventory (read)
 * - PRODUCTION_OP: production (all), bulk (read), inventory (read)
 * - ACCOUNTANT: ningún write, solo reads públicos
 *
 * DIFFERENCES con SecurityConfigProductAccessTest:
 * - Ese test verifica endpoints públicos (permitAll).
 * - Este test verifica endpoints protegidos con roles específicos.
 * - Ambos comparten el patrón @SpringBootTest + @AutoConfigureMockMvc.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacRoleBasedAccessTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private BulkProductService bulkProductService;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private SaleService saleService;

    @MockBean
    private ProductionBatchService productionBatchService;

    @MockBean
    private ReceiptProcessingService receiptProcessingService;

    @MockBean
    private ReceiptConfirmationService receiptConfirmationService;

    /**
     * Limpia el SecurityContext después de cada test para evitar
     * que configuraciones manuales se filtren a otros tests.
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ================================================================
    // HELPERS — Configuran mocks mínimos para tests 200/201
    // ================================================================

    private void mockProductCreate() {
        when(productService.createProduct(any()))
                .thenReturn(new ProductResponse(1L, "SKU-001", "Test", BigDecimal.TEN, true));
    }

    private void mockBulkProductCreate() {
        when(bulkProductService.createBulkProduct(any()))
                .thenReturn(new BulkProductResponse(1L, "Test Bulk", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, true));
    }

    private void mockBulkProductGet() {
        when(bulkProductService.getAllBulkProducts())
                .thenReturn(List.of(new BulkProductResponse(1L, "Test Bulk", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, true)));
    }

    private void mockInventoryGet() {
        when(inventoryService.getInventory(eq(1L)))
                .thenReturn(new InventoryResponse(1L, "Test Product", new BigDecimal("50"), BigDecimal.ZERO));
    }

    private void mockInventoryAdjust() {
        when(inventoryService.adjustStock(eq(1L), any()))
                .thenReturn(new InventoryResponse(1L, "Test Product", new BigDecimal("60"), BigDecimal.ZERO));
    }

    private void mockSaleCreate() {
        when(saleService.createSale(any()))
                .thenReturn(new SaleResponse(1L, BigDecimal.TEN, LocalDateTime.now(), List.of()));
    }

    private void mockProductionCreate() {
        when(productionBatchService.createProductionBatch(any()))
                .thenReturn(new ProductionBatchResponse(
                        1L, 1L, "Product", 1L, "Bulk",
                        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE,
                        BigDecimal.ONE, Instant.now()));
    }

    private void mockProductionGet() {
        when(productionBatchService.getBatchById(eq(1L)))
                .thenReturn(new ProductionBatchResponse(
                        1L, 1L, "Product", 1L, "Bulk",
                        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE,
                        BigDecimal.ONE, Instant.now()));
    }

    private void mockReceiptConfirm() {
        when(receiptConfirmationService.confirm(any(), any()))
                .thenReturn(new PurchaseResponse(1L, "url", "Supplier", null, BigDecimal.TEN, List.of()));
    }

    private String validReceiptConfirmJson() {
        return """
                {
                    "imageUrl": "http://example.com/receipt.jpg",
                    "supplierName": "Proveedor Test",
                    "purchaseDate": "2025-01-01",
                    "lines": [{"description": "Producto A", "quantity": 5, "unitPrice": 10.00}]
                }
                """;
    }

    // ================================================================
    // 7.1 SALES_CLERK — POST sale OK, POST product FORBIDDEN
    // ================================================================

    @Test
    @WithMockUser(roles = "SALES_CLERK")
    void salesClerk_CreateSale_Returns201() throws Exception {
        mockSaleCreate();
        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":5}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "SALES_CLERK")
    void salesClerk_CreateProduct_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"TST\",\"name\":\"Test\",\"minPrice\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_CLERK")
    void salesClerk_AdjustInventory_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADJUSTMENT\",\"quantity\":5,\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SALES_CLERK")
    void salesClerk_GetInventory_Returns200() throws Exception {
        // SALES_CLERK tiene acceso de lectura al inventario
        mockInventoryGet();
        mockMvc.perform(get("/api/v1/inventory/1"))
                .andExpect(status().isOk());
    }

    // ================================================================
    // 7.2 STOCK_MANAGER — POST product OK, POST sale FORBIDDEN
    // ================================================================

    @Test
    @WithMockUser(roles = "STOCK_MANAGER")
    void stockManager_CreateProduct_Returns201() throws Exception {
        mockProductCreate();
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"TST\",\"name\":\"Test\",\"minPrice\":10}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STOCK_MANAGER")
    void stockManager_CreateSale_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STOCK_MANAGER")
    void stockManager_UserManagement_Returns403() throws Exception {
        // STOCK_MANAGER NO debe poder gestionar usuarios (solo ADMIN)
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STOCK_MANAGER")
    void stockManager_GetInventory_Returns200() throws Exception {
        mockInventoryGet();
        mockMvc.perform(get("/api/v1/inventory/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STOCK_MANAGER")
    void stockManager_AdjustInventory_Returns200() throws Exception {
        mockInventoryAdjust();
        mockMvc.perform(post("/api/v1/inventory/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADJUSTMENT\",\"quantity\":5,\"reason\":\"restock\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STOCK_MANAGER")
    void stockManager_GetBulkProducts_Returns200() throws Exception {
        mockBulkProductGet();
        mockMvc.perform(get("/api/v1/bulk-products"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STOCK_MANAGER")
    void stockManager_CreateBulkProduct_Returns201() throws Exception {
        mockBulkProductCreate();
        mockMvc.perform(post("/api/v1/bulk-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"currentStockLiters\":10,\"costPerLiter\":5,\"conversionRatio\":2}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STOCK_MANAGER")
    void stockManager_GetProductionBatch_Returns200() throws Exception {
        mockProductionGet();
        mockMvc.perform(get("/api/v1/production-batches/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STOCK_MANAGER")
    void stockManager_ConfirmReceipt_Returns201() throws Exception {
        // WHAT: STOCK_MANAGER puede confirmar compras (receipts/confirm).
        // El ReceiptController requiere un User de dominio en el SecurityContext
        // para getPrincipal(). Configuramos manualmente un User real con ROLE_STOCK_MANAGER.
        User stockManagerUser = new User("stockmgr", "stock@mundolimpio.com", "pass", Role.STOCK_MANAGER);
        stockManagerUser.setId(2L);

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        stockManagerUser, null, stockManagerUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        mockReceiptConfirm();
        mockMvc.perform(post("/api/v1/receipts/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReceiptConfirmJson()))
                .andExpect(status().isCreated());
    }

    // ================================================================
    // 7.3 PRODUCTION_OP — POST production OK, POST product FORBIDDEN
    // ================================================================

    @Test
    @WithMockUser(roles = "PRODUCTION_OP")
    void productionOp_CreateProduction_Returns201() throws Exception {
        mockProductionCreate();
        mockMvc.perform(post("/api/v1/production-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"bulkProductId\":1,\"rawQuantityUsed\":10}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "PRODUCTION_OP")
    void productionOp_CreateProduct_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"TST\",\"name\":\"Test\",\"minPrice\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PRODUCTION_OP")
    void productionOp_GetProductionBatch_Returns200() throws Exception {
        mockProductionGet();
        mockMvc.perform(get("/api/v1/production-batches/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PRODUCTION_OP")
    void productionOp_GetBulkProducts_Returns200() throws Exception {
        mockBulkProductGet();
        mockMvc.perform(get("/api/v1/bulk-products"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PRODUCTION_OP")
    void productionOp_GetInventory_Returns200() throws Exception {
        mockInventoryGet();
        mockMvc.perform(get("/api/v1/inventory/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PRODUCTION_OP")
    void productionOp_CreateSale_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":5}"))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    // 7.4 ACCOUNTANT — GET sales→200 (read), POST product FORBIDDEN
    // ================================================================

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountant_CreateProduct_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"TST\",\"name\":\"Test\",\"minPrice\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountant_CreateSale_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountant_AdjustInventory_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADJUSTMENT\",\"quantity\":5,\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountant_GetInventory_Returns403() throws Exception {
        // ACCOUNTANT NO tiene acceso de lectura al inventario según matriz
        mockMvc.perform(get("/api/v1/inventory/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountant_GetProducts_Returns200() throws Exception {
        // Productos son públicos (permitAll en SecurityConfig)
        mockProductCreate(); // Necesario por si el servicio se llama (no debería para GET)
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    // ================================================================
    // 7.5 ADMIN — acceso total, incluyendo gestión de usuarios
    // ================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_CreateProduct_Returns201() throws Exception {
        mockProductCreate();
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ADM\",\"name\":\"Admin Product\",\"minPrice\":10}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_GetUsers_Returns200() throws Exception {
        // ADMIN sí puede acceder a gestión de usuarios
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_CreateSale_Returns201() throws Exception {
        mockSaleCreate();
        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":5}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_CreateProduction_Returns201() throws Exception {
        mockProductionCreate();
        mockMvc.perform(post("/api/v1/production-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"bulkProductId\":1,\"rawQuantityUsed\":10}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_AdjustInventory_Returns200() throws Exception {
        mockInventoryAdjust();
        mockMvc.perform(post("/api/v1/inventory/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADJUSTMENT\",\"quantity\":5,\"reason\":\"restock\"}"))
                .andExpect(status().isOk());
    }

    // ================================================================
    // STOCK_OPERATOR — receipt process OK, product FORBIDDEN, inventory read OK
    // ================================================================

    @Test
    @WithMockUser(roles = "STOCK_OPERATOR")
    void stockOperator_CreateProduct_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"TST\",\"name\":\"Test\",\"minPrice\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STOCK_OPERATOR")
    void stockOperator_GetInventory_Returns200() throws Exception {
        mockInventoryGet();
        mockMvc.perform(get("/api/v1/inventory/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STOCK_OPERATOR")
    void stockOperator_AdjustInventory_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADJUSTMENT\",\"quantity\":5,\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    // UNAUTHENTICATED — 401 en todos los endpoints protegidos
    // ================================================================

    @Test
    void unauthenticated_PostProduct_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"TST\",\"name\":\"Test\",\"minPrice\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_PostSale_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_PostInventory_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADJUSTMENT\",\"quantity\":5,\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }
}
