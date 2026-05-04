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
 * BulkProductController expone los endpoints para la gestión de materia.
 *
 * Solo accesible por ADMIN.
 *
 * Mapeo:
 * POST   /api/v1/bulk-products              → Crear materia prima
 * GET    /api/v1/bulk-products              → Listar todas
 * GET    /api/v1/bulk-products/{id}              → Obtener por ID
 * PUT    /api/v1/bulk-products/{id}              → Actualizar
 * DELETE /api/v1/bulk-products/{id}              → Eliminar (soft delete)
 *
 */
@RestController
@RequestMapping("/api/v1/bulk-products")
@Tag(name = "Bulk Products", description = "Endpoints para la gestión de la materia prima (ADMIN only)")
public class BulkProductController {

    private final BulkProductService service;

    public BulkProductController(BulkProductService service) {
        this.service = service;
    }

    // ========================= CREATE =========================

    /**
     * Crea una nueva materia prima.
     *
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create a new bulk product",
            description = "Creates a new raw material with name, stok, cost and conversion ratio. Only ADMIN can access."
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
     * Obtiene todos las materias primas.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all bulk products",
            description = "Retrieves a list of all raw materials. Only ADMINM can access. " +
                    "Useful for administrative purposes and reporting."
    )
    @ApiResponse(responseCode = "200", description = "List of bulk products")
    public ResponseEntity<List<BulkProductResponse>> getAllBulkProducts() {
        return ResponseEntity.ok(service.getAllBulkProducts());
    }

    /**
     * Obtiene una materia prima su ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get bulk product by ID",
            description = "Retrieves a raw material using its unique identifier (ID). Only ADMIN can Access."
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
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update a bulk product",
            description = "Updates an existing raw naterial. All fiels are required. Only ADMIN can access."
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
     * Elimina una materia prima
     * Solo deberia eliminarse si no tiene loteas asociados
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete a bulk product",
            description = "Deletes a raw material. Only ADMIN can access. " +
                    "Warning: Should not be deleted if it has associated production batches."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bulk product deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Bulk product not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN  can access")
    })
    public ResponseEntity<Void> deleteBulkProduct(@PathVariable Long id) {
        service.deleteBulkProduct(id);
        return ResponseEntity.noContent().build();
    }
}