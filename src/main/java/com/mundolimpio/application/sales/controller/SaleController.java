package com.mundolimpio.application.sales.controller;

import com.mundolimpio.application.sales.dto.SaleRequest;
import com.mundolimpio.application.sales.dto.SaleResponse;
import com.mundolimpio.application.sales.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * SaleController expone los endpoints para la gestión de ventas.
 *
 * POR QUÉ ESTA IMPLEMENTACIÓN:
 * - Seguimos exactamente el patrón de BulkProductController para consistencia.
 * - @PreAuthorize("hasRole('ADMIN')") asegura que solo ADMIN pueda crear ventas.
 *   El OPERATOR no tiene acceso — su rol es solo consultar (futuros endpoints GET).
 * - @Valid valida el SaleRequest automáticamente antes de entrar al método.
 *   Si productId es null o quantity <= 0, retorna 400 sin ejecutar lógica.
 * - ResponseEntity.status(HttpStatus.CREATED) es el estándar REST: POST exitoso = 201.
 *
 * Mapeo:
 * POST /api/v1/sales  → Crear venta (ADMIN only)
 */
@RestController
@RequestMapping("/api/v1/sales")
@Tag(name = "Sales", description = "Endpoints para la gestión de ventas (ADMIN only)")
public class SaleController {

    private final SaleService service;

    /**
     * Constructor con inyección de dependencias.
     * Spring inyecta SaleService automáticamente.
     */
    public SaleController(SaleService service) {
        this.service = service;
    }

    // ========================= CREATE =========================

    /**
     * Crea una nueva venta con lógica FIFO.
     *
     * CÓMO FUNCIONA EL FLUJO:
     * 1. @Valid verifica que el request tenga productId != null y quantity > 0.
     *    Si falla → 400 Bad Request automático.
     * 2. @PreAuthorize verifica que el usuario tenga ROLE_ADMIN.
     *    Si es OPERATOR → 403 Forbidden.
     * 3. SaleService.createSale() aplica FIFO: descuenta stock de lotes viejos primero.
     * 4. Si no hay stock suficiente → IllegalArgumentException → 400 Bad Request.
     * 5. Si hay concurrencia (dos ventas simultáneas al mismo lote) → 500 (se mejora después).
     *
     * POR QUÉ ResponseEntity.status(CREATED):
     * - 200 OK es para GET (lectura). POST que crea recursos debe retornar 201 Created.
     * - El body contiene el SaleResponse con id, totalAmount, items.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create a new sale",
            description = "Creates a new sale with FIFO stock deduction. Only ADMIN can access. " +
                    "Stock is deducted from the oldest production batches first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sale created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request: validation failed or insufficient stock"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<SaleResponse> createSale(@Valid @RequestBody SaleRequest request) {
        SaleResponse response = service.createSale(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
