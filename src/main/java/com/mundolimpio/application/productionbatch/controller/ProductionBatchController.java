package com.mundolimpio.application.productionbatch.controller;

import com.mundolimpio.application.productionbatch.dto.ProductionBatchRequest;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchResponse;
import com.mundolimpio.application.productionbatch.service.ProductionBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * ProductionBatchController expone los endpoints para la gestión de lotes de producción.
 *
 * Accesible por ADMIN, STOCK_MANAGER (lectura) y PRODUCTION_OP (escritura/lectura).
 *
 * Mapeo:
 * POST   /api/v1/production-batches              → Crear lote (producción)
 * GET    /api/v1/production-batches              → Listar todos
 * GET    /api/v1/production-batches/product/{productId} → Lotes por producto
 * GET    /api/v1/production-batches/{id}          → Obtener por ID
 */
@RestController
@RequestMapping("/api/v1/production-batches")
@Tag(name = "Production Batches", description = "Endpoints para la gestión de lotes de producción (ADMIN, STOCK_MANAGER, PRODUCTION_OP)")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCTION_OP')")
    // WHAT: Operador de produccion (PRODUCTION_OP) puede crear lotes
    // WHY: El operador ejecuta la produccion y registra los lotes resultantes
    @Operation(
            summary = "Create a new production batch",
            description = "Creates a new production batch using raw materials. " +
                    "Applies conversion ratio to calculate finished product. ADMIN and PRODUCTION_OP can access."
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
     * Obtiene todos los lotes de producción con paginación.
     * Ordenados por fecha de producción descendente (nuevo primero).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER', 'PRODUCTION_OP')")
    // WHAT: Listado completo de lotes (GET /api/v1/production-batches)
    // WHY: El frontend Flutter necesita listar todos los lotes para la pantalla de producción
    @Operation(
            summary = "Get all production batches",
            description = "Retrieves a paginated list of production batches ordered by production date descending (newest first). ADMIN, STOCK_MANAGER, and PRODUCTION_OP can access."
    )
    @ApiResponse(responseCode = "200", description = "Paginated list of production batches")
    public ResponseEntity<Page<ProductionBatchResponse>> getAllProductionBatches(
            @PageableDefault(size = 20, sort = "productionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.getAllProductionBatches(pageable));
    }

    /**
     * Obtiene todos los lotes de un producto específico con paginación.
     */
    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER', 'PRODUCTION_OP')")
    // WHAT: Consulta de lotes ampliada a STOCK_MANAGER y PRODUCTION_OP
    // WHY: STOCK_MANAGER audita produccion; PRODUCTION_OP consulta su propio trabajo
    @Operation(
            summary = "Get batches by product",
            description = "Retrieves a paginated list of production batches for a specific product. ADMIN, STOCK_MANAGER, and PRODUCTION_OP can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of batches"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<Page<ProductionBatchResponse>> getBatchesByProductId(
            @PathVariable Long productId,
            @PageableDefault(size = 20, sort = "productionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.getBatchesByProductId(productId, pageable));
    }

    /**
     * Obtiene un lote por su ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER', 'PRODUCTION_OP')")
    // WHAT: Consulta de lote por ID ampliada (mismos roles que GET /product/{productId})
    @Operation(
            summary = "Get production batch by ID",
            description = "Retrieves a production batch by its ID. ADMIN, STOCK_MANAGER, and PRODUCTION_OP can access."
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
