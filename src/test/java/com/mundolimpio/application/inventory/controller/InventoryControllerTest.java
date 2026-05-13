package com.mundolimpio.application.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mundolimpio.application.inventory.dto.AdjustmentRequest;
import com.mundolimpio.application.inventory.dto.InventoryResponse;
import com.mundolimpio.application.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests para InventoryController usando @SpringBootTest + @AutoConfigureMockMvc.
 *
 * QUE HACE: Verifica que los endpoints del controlador responden
 * correctamente a nivel HTTP: status codes, headers, body JSON.
 * El servicio es mockeado para aislar la capa web.
 *
 * POR QUE @SpringBootTest en vez de @WebMvcTest:
 * - @WebMvcTest no carga el SecurityConfig completo, y el
 *   JwtAuthenticationFilter (que es @Component) no puede crearse
 *   sin JwtService en el contexto reducido de @WebMvcTest.
 * - AuthControllerTest ya establece este patrón: @SpringBootTest
 *   con @AutoConfigureMockMvc + @MockBean para el servicio.
 * - @WithMockUser(roles = "ADMIN") proporciona autenticación simulada
 *   para probar endpoints protegidos sin JWT real.
 *
 * DIFERENCIA con AuthControllerTest:
 * - AuthControllerTest mockea AuthService.
 * - InventoryControllerTest mockea InventoryService.
 * - Ambos usan el mismo patrón de test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    // ==================== GET INVENTORY TESTS ====================

    /**
     * Test 1: GET /api/v1/inventory/{productId} con ADMIN retorna 200.
     *
     * QUE VERIFICA:
     * - Service mockeado retorna InventoryResponse.
     * - Status 200 OK.
     * - Body contiene los campos esperados en JSON.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void getInventory_AsAdmin_Returns200() throws Exception {
        // Given: el servicio mockeado retorna un inventory response
        Long productId = 1L;
        InventoryResponse mockResponse = new InventoryResponse(
                productId, "Lavandina 3L", new BigDecimal("50"), BigDecimal.ZERO);

        when(inventoryService.getInventory(productId)).thenReturn(mockResponse);

        // When/Then: GET /api/v1/inventory/1 → 200 OK con los datos correctos
        mockMvc.perform(get("/api/v1/inventory/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.productName").value("Lavandina 3L"))
                .andExpect(jsonPath("$.currentStock").value(50))
                .andExpect(jsonPath("$.minStockThreshold").value(0));
    }

    /**
     * Test 2: GET /api/v1/inventory/{productId} sin autenticación retorna 401.
     *
     * QUE VERIFICA:
     * - Sin token de autenticación, el endpoint protegido retorna 401.
     *
     * POR QUE 401 y no 403:
     * - 401 = no autenticado (no hay token).
     * - 403 = autenticado pero sin el rol requerido.
     * - SecurityConfig define HttpStatusEntryPoint(UNAUTHORIZED).
     */
    @Test
    void getInventory_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/1"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== LOW STOCK TESTS ====================

    /**
     * Test 3: GET /api/v1/inventory/low-stock con ADMIN retorna 200.
     *
     * QUE VERIFICA:
     * - Service mockeado retorna lista de low stock.
     * - Status 200 OK.
     * - Body es un array JSON con los items esperados.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void getLowStock_AsAdmin_Returns200() throws Exception {
        // Given: el servicio retorna una lista con un producto low-stock
        List<InventoryResponse> mockList = List.of(
                new InventoryResponse(1L, "Producto A", new BigDecimal("3"), new BigDecimal("10"))
        );

        when(inventoryService.getLowStockInventories()).thenReturn(mockList);

        // When/Then: GET /low-stock → 200 OK con array JSON
        mockMvc.perform(get("/api/v1/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].productName").value("Producto A"))
                .andExpect(jsonPath("$[0].currentStock").value(3));
    }

    // ==================== ADJUST STOCK TESTS ====================

    /**
     * Test 4: POST /api/v1/inventory/{productId}/adjust con body válido retorna 200.
     *
     * QUE VERIFICA:
     * - Service.adjustStock() retorna el response actualizado.
     * - Status 200 OK.
     * - Body contiene el nuevo stock después del ajuste.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void adjustStock_ValidRequest_Returns200() throws Exception {
        // Given: un ajuste válido
        Long productId = 1L;
        String requestJson = """
                {
                    "type": "ADJUSTMENT",
                    "quantity": 10,
                    "reason": "Restock inventory"
                }
                """;

        InventoryResponse mockResponse = new InventoryResponse(
                productId, "Lavandina 3L", new BigDecimal("60"), BigDecimal.ZERO);

        when(inventoryService.adjustStock(eq(productId), any(AdjustmentRequest.class)))
                .thenReturn(mockResponse);

        // When/Then: POST /api/v1/inventory/1/adjust → 200 OK con stock actualizado
        mockMvc.perform(post("/api/v1/inventory/{productId}/adjust", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.currentStock").value(60));
    }

    /**
     * Test 5: POST /api/v1/inventory/{productId}/adjust con body inválido retorna 400.
     *
     * QUE VERIFICA:
     * - Jakarta Validation (@NotBlank, @NotNull) en AdjustmentRequest
     *   rechaza un body vacío o con campos faltantes.
     * - Status 400 Bad Request.
     * - Body contiene error de validación con código VALIDATION_ERROR.
     *
     * POR QUE @Valid en el controller:
     * - Sin @Valid, los campos inválidos llegarían al servicio como null.
     * - @Valid activa Jakarta Validation antes de ejecutar el método.
     * - MethodArgumentNotValidException → GlobalExceptionHandler → 400.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void adjustStock_InvalidBody_Returns400() throws Exception {
        // Given: un body vacío (sin campos requeridos)
        String emptyBody = "{}";

        // When/Then: POST con body vacío → 400 Bad Request
        mockMvc.perform(post("/api/v1/inventory/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * Test 6: POST /api/v1/inventory/{productId}/adjust sin auth retorna 401.
     *
     * QUE VERIFICA:
     * - Sin autenticación, el endpoint POST también retorna 401.
     */
    @Test
    void adjustStock_WithoutAuth_Returns401() throws Exception {
        String validBody = """
                {
                    "type": "ADJUSTMENT",
                    "quantity": 5,
                    "reason": "Test"
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test 7: GET /api/v1/inventory/low-stock sin auth retorna 401.
     *
     * QUE VERIFICA:
     * - Todos los endpoints requieren autenticación, incluido GET /low-stock.
     */
    @Test
    void getLowStock_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/low-stock"))
                .andExpect(status().isUnauthorized());
    }
}
