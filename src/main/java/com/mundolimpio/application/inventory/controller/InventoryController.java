package com.mundolimpio.application.inventory.controller;

import com.mundolimpio.application.inventory.dto.AdjustmentRequest;
import com.mundolimpio.application.inventory.dto.InventoryResponse;
import com.mundolimpio.application.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para el módulo de inventario.
 *
 * QUE HACE: Expone endpoints para consultar y modificar el stock
 * de productos. Todos los endpoints requieren rol ADMIN.
 *
 * POR QUE seguimos el patrón de SaleController:
 *   - @PreAuthorize("hasRole('ADMIN')") en cada método (no a nivel
 *     de clase) para mantener flexibilidad si en el futuro algún
 *     endpoint necesita un rol diferente.
 *   - Constructor injection (no @Autowired en campos).
 *   - Swagger/OpenAPI annotations para documentación automática.
 *
 * DIFERENCIA con SaleController:
 *   - SaleController solo tiene POST (creación).
 *   - InventoryController tiene GET (consulta) y POST (ajuste).
 *   - GET /low-stock es una query de negocio específica (no CRUD).
 *
 * DIFERENCIA con endpoints de ProductController:
 *   - ProductController tiene endpoints públicos (permitAll()).
 *   - InventoryController requiere ADMIN en TODOS los endpoints.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Endpoints para gestión de inventario (ADMIN only)")
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Constructor con inyección de dependencias.
     * Spring inyecta InventoryService automáticamente.
     */
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // ========================= GET BY PRODUCT =========================

    /**
     * Obtiene el inventario actual de un producto por su ID.
     *
     * QUE HACE: Retorna el stock actual y umbral mínimo del producto.
     * Si el producto no tiene inventario, retorna 404.
     *
     * CÓMO FUNCIONA EL FLUJO:
     * 1. @PreAuthorize verifica que el usuario tenga ROLE_ADMIN.
     * 2. InventoryService.getInventory() busca por productId.
     * 3. Si no existe → InventoryNotFoundException → 404.
     *
     * @param productId ID del producto
     * @return 200 OK con InventoryResponse, o 404 si no existe
     */
    @GetMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get inventory by product ID",
            description = "Returns the current stock and minimum threshold for a product. " +
                    "Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: only ADMIN can access"),
            @ApiResponse(responseCode = "404", description = "Inventory not found for the given product ID")
    })
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable Long productId) {
        InventoryResponse response = inventoryService.getInventory(productId);
        return ResponseEntity.ok(response);
    }

    // ========================= LOW STOCK =========================

    /**
     * Obtiene todos los productos con stock por debajo del umbral mínimo.
     *
     * QUE HACE: Retorna una lista de productos cuyo currentStock es menor
     * que su minStockThreshold. Vacía si ningún producto está en bajo stock.
     *
     * POR QUE es un GET sin path variable:
     * - /low-stock es un sub-recurso de /inventory (consulta de negocio).
     * - No recibe parámetros de ruta porque la query es global.
     *
     * @return 200 OK con lista (puede ser vacía)
     */
    @GetMapping("/low-stock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get low stock inventories",
            description = "Returns all products where current stock is below the minimum threshold. " +
                    "Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of low-stock inventories (may be empty)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: only ADMIN can access")
    })
    public ResponseEntity<List<InventoryResponse>> getLowStockInventories() {
        List<InventoryResponse> lowStockItems = inventoryService.getLowStockInventories();
        return ResponseEntity.ok(lowStockItems);
    }

    // ========================= ADJUST STOCK =========================

    /**
     * Ajusta manualmente el stock de un producto con auditoría.
     *
     * QUE HACE: Aplica un ajuste manual al stock del producto y registra
     * el cambio en el trail de auditoría (InventoryAdjustment).
     *
     * CÓMO FUNCIONA EL FLUJO:
     * 1. @Valid valida el AdjustmentRequest: type no vacío, quantity
     *    no null, reason no vacío. Si falla → 400 Bad Request.
     * 2. @PreAuthorize verifica ROLE_ADMIN. Si no → 403.
     * 3. InventoryService.adjustStock() aplica la lógica de negocio.
     * 4. Si el stock quedaría negativo → InvalidAdjustmentException → 400.
     * 5. Si hay concurrencia (dos ajustes simultáneos) → 409.
     *
     * POR QUE POST en vez de PUT/PATCH:
     * - Cada ajuste es una NUEVA acción en el audit trail.
     * - No estamos actualizando un recurso existente, estamos creando
     *   un nuevo evento de ajuste que modifica el estado del inventario.
     * - PUT implica reemplazo completo (no es el caso).
     * - PATCH sería semánticamente correcto pero POST es más común para
     *   acciones de negocio (como "ajustar stock").
     *
     * @param productId ID del producto a ajustar
     * @param request   Body con type, quantity (con signo), reason
     * @return 200 OK con InventoryResponse actualizado
     */
    @PostMapping("/{productId}/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Adjust stock manually",
            description = "Manually adjusts the stock of a product with an audit trail. " +
                    "Quantity uses sign convention: positive = increase, negative = decrease. " +
                    "Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock adjusted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request: validation failed or insufficient stock"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: only ADMIN can access"),
            @ApiResponse(responseCode = "404", description = "Inventory not found for the given product ID"),
            @ApiResponse(responseCode = "409", description = "Concurrent modification detected, retry the request")
    })
    public ResponseEntity<InventoryResponse> adjustStock(
            @PathVariable Long productId,
            @Valid @RequestBody AdjustmentRequest request) {
        InventoryResponse response = inventoryService.adjustStock(productId, request);
        return ResponseEntity.ok(response);
    }
}
