package com.mundolimpio.application.product.service;

import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.dto.ProductRequest;
import com.mundolimpio.application.product.dto.ProductResponse;
import com.mundolimpio.application.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository){
        this.productRepository = productRepository;
    }

    public ProductResponse createProduct(ProductRequest request) {
        //Product product = productRepository.existsBySku(request.sku());
        if (productRepository.existsBySku(request.sku())) {
            throw new RuntimeException("Product with this SKU already exists");
        }

        // Creamos un nuevo producto

        Product product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setMinPrice(request.minPrice());
        product.setActive(true);

        // Guardamos en la base de datos
        Product savedProduct = productRepository.save(product);

        // Convertimos a DTO

        return new ProductResponse(
                savedProduct.getId(),
                savedProduct.getSku(),
                savedProduct.getName(),
                savedProduct.getMinPrice(),
                savedProduct.getActive()
        );

    }

    public ProductResponse getBySku(String sku){
        Product product = productRepository.findBySku(sku).orElseThrow(() -> new RuntimeException("Product not found"));

        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getMinPrice(),
                product.getActive()
        );
    }
}
