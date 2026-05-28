package com.mundolimpio.application.bulkproduct.controller;

import com.mundolimpio.application.bulkproduct.dto.BulkProductRequest;
import com.mundolimpio.application.bulkproduct.dto.BulkProductResponse;
import com.mundolimpio.application.bulkproduct.service.BulkProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BulkProductController expone los endpoints para la gestión de materia prima.
 *
 * Accesible por ADMIN, STOCK_MANAGER (escritura/lectura) y PRODUCTION_OP (solo lectura).
 *
 * Mapeo:
 * POST   /api/v1/bulk-products              → Crear materia prima
 * GET    /api/v1/bulk-products              → Listar activas
 * GET    /api/v1/bulk-products/all          → Listar todas (admin)
 * GET    /api/v1/bulk-products/{id}         → Obtener por ID
 * PUT    /api/v1/bulk-products/{id}         → Actualizar
 * DELETE /api/v1/bulk-products/{id}         → Soft delete (marca active=false)
 * PATCH  /api/v1/bulk-products/{id}/reactivate → Reactivar
 */
@RestController
@RequestMapping("/api/v1/bulk-products")
@Tag(name = "Bulk Products", description = "Endpoints para la gestión de la materia prima (ADMIN, STOCK_MANAGER, PRODUCTION_OP)")
public class BulkProductController {

    private final BulkProductService service;

    public BulkProductController(BulkProductService service) {
        this.service = service;
    }

    // ========================= CREATE =========================

    /**
     * Crea una nueva materia prima.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER')")
    // WHAT: STOCK_MANAGER puede crear materia prima
    // WHY: Responsable de gestionar el inventario de insumos
    @Operation(
            summary = "Create a new bulk product",
            description = "Creates a new raw material with name, stock, cost and conversion ratio. ADMIN and STOCK_MANAGER can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bulk product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request: validation failed"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<BulkProductResponse> createBulkProduct(@Valid @RequestBody BulkProductRequest request) {
        BulkProductResponse response = service.createBulkProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ========================= READ =========================

    /**
     * Obtiene todas las materias primas activas.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER', 'PRODUCTION_OP')")
    // WHAT: Lectura ampliada para STOCK_MANAGER y PRODUCTION_OP
    // WHY: STOCK_MANAGER gestiona inventario; PRODUCTION_OP necesita ver materia prima disponible
    @Operation(
            summary = "Get all active bulk products",
            description = "Retrieves a list of all active raw materials (active=true). ADMIN, STOCK_MANAGER, and PRODUCTION_OP can access. " +
                    "Useful for administrative purposes and reporting."
    )
    @ApiResponse(responseCode = "200", description = "List of active bulk products")
    public ResponseEntity<List<BulkProductResponse>> getAllBulkProducts() {
        return ResponseEntity.ok(service.getAllBulkProducts());
    }

    /**
     * Obtiene todas las materias primas (activas e inactivas).
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER', 'PRODUCTION_OP')")
    // WHAT: Lectura completa (activos e inactivos) para los mismos roles que GET /
    @Operation(
            summary = "Get all bulk products (active and inactive)",
            description = "Retrieves a complete list of all raw materials, including inactive ones. " +
                    "Useful for administrative purposes and reporting."
    )
    @ApiResponse(responseCode = "200", description = "Complete list of all bulk products")
    public ResponseEntity<List<BulkProductResponse>> getAllBulkProductsAdmin() {
        return ResponseEntity.ok(service.getAllBulkProductsAdmin());
    }

    /**
     * Obtiene una materia prima por su ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER', 'PRODUCTION_OP')")
    // WHAT: Consulta por ID ampliada para STOCK_MANAGER y PRODUCTION_OP
    @Operation(
            summary = "Get bulk product by ID",
            description = "Retrieves a raw material using its unique identifier (ID). ADMIN, STOCK_MANAGER, and PRODUCTION_OP can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk Product found"),
            @ApiResponse(responseCode = "404", description = "Bulk Product not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<BulkProductResponse> getBulkProductById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getBulkProductById(id));
    }

    // ========================= UPDATE =========================

    /**
     * Actualiza una materia prima existente.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER')")
    // WHAT: STOCK_MANAGER puede actualizar materia prima
    @Operation(
            summary = "Update a bulk product",
            description = "Updates an existing raw material. All fields are required. ADMIN and STOCK_MANAGER can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request: validation failed"),
            @ApiResponse(responseCode = "404", description = "Bulk product not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<BulkProductResponse> updateBulkProduct(
            @PathVariable Long id,
            @Valid @RequestBody BulkProductRequest request
    ) {
        return ResponseEntity.ok(service.updateBulkProduct(id, request));
    }

    // ========================= DELETE =========================

    /**
     * Realiza un soft delete de una materia prima (marca como inactivo).
     * No elimina el registro, solo marca active = false.
     *
     * Razón: Mantiene integridad referencial con production_batches y purchase_items.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER')")
    // WHAT: STOCK_MANAGER puede dar de baja materia prima (soft delete)
    @Operation(
            summary = "Soft delete a bulk product",
            description = "Performs a soft delete: marks the raw material as inactive (active=false). " +
                    "The record is NOT deleted from the database, preserving data integrity " +
                    "for production batches and purchase history. " +
                    "Use PATCH /reactivate to restore the material if needed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bulk product marked as inactive successfully"),
            @ApiResponse(responseCode = "404", description = "Bulk product not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<Void> deleteBulkProduct(@PathVariable Long id) {
        service.deleteBulkProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reactiva una materia prima marcada como inactiva.
     */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER')")
    // WHAT: STOCK_MANAGER puede reactivar materia prima inactiva
    @Operation(
            summary = "Reactivate an inactive bulk product",
            description = "Reverses a soft delete: marks the raw material as active (active=true). " +
                    "This restores the material to the active products list."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bulk product reactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Bulk product not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<Void> reactivateBulkProduct(@PathVariable Long id) {
        service.reactivateBulkProduct(id);
        return ResponseEntity.noContent().build();
    }
}
