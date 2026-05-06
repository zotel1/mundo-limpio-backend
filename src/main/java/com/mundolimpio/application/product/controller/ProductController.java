package com.mundolimpio.application.product.controller;

import com.mundolimpio.application.product.dto.ProductRequest;
import com.mundolimpio.application.product.dto.ProductResponse;
import com.mundolimpio.application.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ProductController expone los endpoints para la gestión de productos.
 *
 * Mapeo:
 * POST   /api/v1/products              → Crear producto
 * GET    /api/v1/products/{id}         → Obtener por ID
 * GET    /api/v1/products/sku/{sku}    → Obtener por SKU
 * GET    /api/v1/products              → Listar todos (activos)
 * PUT    /api/v1/products/{id}         → Actualizar
 * DELETE /api/v1/products/{id}         → Eliminar (soft delete)
 * PATCH  /api/v1/products/{id}/reactivate → Reactivar
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Endpoints para la gestión del catálogo de productos")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ========================= CREATE =========================

    /**
     * Crea un nuevo producto.
     *
     * @param request Los datos del producto (SKU, nombre, precio mínimo)
     * @return ProductResponse con el producto creado (201 CREATED)
     */
    @PostMapping
    @Operation(
            summary = "Create a new product",
            description = "Creates a new product with unique SKU. SKU must contain only uppercase letters, numbers, and hyphens."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request: validation failed (empty SKU, invalid price, etc)"),
            @ApiResponse(responseCode = "409", description = "Conflict: Product with this SKU already exists")
    })
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ========================= READ =========================

    /**
     * Obtiene un producto por su ID.
     *
     * @param id El identificador único del producto
     * @return ProductResponse con los datos del producto (200 OK)
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get product by ID",
            description = "Retrieves a product using its unique identifier (ID)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found with the given ID")
    })
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        ProductResponse response = productService.getProductById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene un producto por su SKU.
     *
     * @param sku El Stock Keeping Unit único del producto
     * @return ProductResponse con los datos del producto (200 OK)
     */
    @GetMapping("/sku/{sku}")
    @Operation(
            summary = "Get product by SKU",
            description = "Retrieves a product using its unique SKU (Stock Keeping Unit)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found with the given SKU")
    })
    public ResponseEntity<ProductResponse> getProductBySku(@PathVariable String sku) {
        ProductResponse response = productService.getProductBySku(sku);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene todos los productos activos.
     *
     * @return Lista de ProductResponse con productos activos (200 OK)
     */
    @GetMapping
    @Operation(
            summary = "Get all active products",
            description = "Retrieves a list of all active products (active=true). " +
                    "Useful for inventory management, FIFO operations, and sales."
    )
    @ApiResponse(responseCode = "200", description = "List of active products")
    public ResponseEntity<List<ProductResponse>> getAllActiveProducts() {
        List<ProductResponse> response = productService.getAllActiveProducts();
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene todos los productos (activos e inactivos).
     *
     * @return Lista de ProductResponse con todos los productos (200 OK)
     */
    @GetMapping("/all")
    @Operation(
            summary = "Get all products (active and inactive)",
            description = "Retrieves a complete list of all products, including inactive ones. " +
                    "Useful for administrative purposes and reporting."
    )
    @ApiResponse(responseCode = "200", description = "Complete list of all products")
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> response = productService.getAllProducts();
        return ResponseEntity.ok(response);
    }

    // ========================= UPDATE =========================

    /**
     * Actualiza un producto existente.
     *
     * @param id El ID del producto a actualizar
     * @param request Los nuevos datos del producto
     * @return ProductResponse con el producto actualizado (200 OK)
     */
    @PutMapping("/{id}")
    @Operation(
            summary = "Update an existing product",
            description = "Updates all fields of a product. " +
                    "If the SKU is changed, it must be unique. " +
                    "All fields are required."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request: validation failed"),
            @ApiResponse(responseCode = "404", description = "Product not found with the given ID"),
            @ApiResponse(responseCode = "409", description = "Conflict: New SKU already exists in another product")
    })
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(response);
    }

    // ========================= DELETE =========================

    /**
     * Realiza un soft delete de un producto (marca como inactivo).
     * No elimina el registro, solo marca active = false.
     *
     * Razón: Mantiene integridad referencial con production_batches y sales.
     *
     * @param id El ID del producto a eliminar (soft delete)
     * @return 204 NO_CONTENT
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a product (soft delete)",
            description = "Performs a soft delete: marks the product as inactive (active=false). " +
                    "The record is NOT deleted from the database, preserving data integrity " +
                    "for production batches and sales history. " +
                    "Use PATCH /reactivate to restore the product if needed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product marked as inactive successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found with the given ID")
    })
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProductSoftDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reactiva un producto marcado como inactivo.
     *
     * @param id El ID del producto a reactivar
     * @return 204 NO_CONTENT
     */
    @PatchMapping("/{id}/reactivate")
    @Operation(
            summary = "Reactivate an inactive product",
            description = "Reverses a soft delete: marks the product as active (active=true). " +
                    "This restores the product to the active products list."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product reactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found with the given ID")
    })
    public ResponseEntity<Void> reactivateProduct(@PathVariable Long id) {
        productService.reactivateProduct(id);
        return ResponseEntity.noContent().build();
    }
}
