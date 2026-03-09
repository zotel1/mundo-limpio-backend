package com.mundolimpio.application.product.controller;

import com.mundolimpio.application.product.dto.ProductRequest;
import com.mundolimpio.application.product.dto.ProductResponse;
import com.mundolimpio.application.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    // Inyectamos el servicer (Constructor injection)

    private final ProductService productService;

    public ProductController(ProductService productService){
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        // Llamamos al service y respondemos 201

            ProductResponse response = productService.createProduct(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{sku}")
    public ResponseEntity<ProductResponse> getBySku(@PathVariable String sku) {

        ProductResponse response = productService.getBySku(sku);

        return ResponseEntity.ok(response);
    }
}

