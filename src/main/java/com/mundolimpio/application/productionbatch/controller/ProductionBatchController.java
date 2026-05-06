package com.mundolimpio.application.productionbatch.controller;

import com.mundolimpio.application.productionbatch.dto.ProductionBatchRequest;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchResponse;
import com.mundolimpio.application.productionbatch.service.ProductionBatchService;
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
 * ProductionBatchController expone los endpoints para la gestión de lotes de producción.
 *
 * Solo accesible por ADMIN.
 *
 * Mapeo:
 * POST   /api/v1/production-batches              → Crear lote (producción)
 * GET    /api/v1/production-batches/product/{productId} → Lotes por producto
 * GET    /api/v1/production-batches/{id}          → Obtener por ID
 */
@RestController
@RequestMapping("/api/v1/production-batches")
@Tag(name = "Production Batches", description = "Endpoints para la gestión de lotes de producción (ADMIN only)")
public class ProductionBatchController {

    private final ProductionBatchService service;

    public ProductionBatchController(ProductionBatchService service) {
        this.service = service;
    }

    /**
     * Crea un nuevo lote de producción.
     * Toma materia prima, aplica conversión y genera el lote.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create a new production batch",
            description = "Creates a new production batch using raw materials. " +
                    "Applies conversion ratio to calculate finished product. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Production batch created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request: validation failed"),
            @ApiResponse(responseCode = "404", description = "Product or Bulk Product not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<ProductionBatchResponse> createProductionBatch(
            @Valid @RequestBody ProductionBatchRequest request) {
        ProductionBatchResponse response = service.createProductionBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Obtiene todos los lotes de un producto específico.
     */
    @GetMapping("/product/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get batches by product",
            description = "Retrieves all production batches for a specific product. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of batches"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<List<ProductionBatchResponse>> getBatchesByProductId(
            @PathVariable Long productId) {
        return ResponseEntity.ok(service.getBatchesByProductId(productId));
    }

    /**
     * Obtiene un lote por su ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get production batch by ID",
            description = "Retrieves a production batch by its ID. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch found"),
            @ApiResponse(responseCode = "404", description = "Batch not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<ProductionBatchResponse> getBatchById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getBatchById(id));
    }
}
