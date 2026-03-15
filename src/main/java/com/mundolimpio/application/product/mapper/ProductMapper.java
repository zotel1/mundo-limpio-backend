package com.mundolimpio.application.product.mapper;

/*
* ProductMapper es responsable de convertir entre:
* - Product (Entity JPA) -> ProductResponse (DTO para respuestas)
* - ProductRequest (DTO de entrada) -> Product (Entity JPA)
*
* De esta forma, la lógica de conversion está centralizada y es reutilizable.*/

import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.dto.ProductResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    /*
    * Convierte una entidad Product a un DTO ProductResponse.
    * Usado para retornar datos en las respuestas HTTP.
    *
    * @param product La entidad Product desde la base de datos
    * @return ProductResponse con los datos listos para enviar al cliente*/

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getMinPrice(),
                product.getActive()
        );
    }

    /*
    * Convierte un DTO ProductRequest a una entidad Product.
    * Usado al crear nuevos productos.
    *
    * @param request El DTO recibido desde el cliente
    * @return Una entidad Product lista para ser persistida*/

    public Product toEntity(ProductResponse request) {
        Product product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setMinPrice(request.minPrice());
        product.setActive(true); // Por defecto, los productos se crean activos
        return product;
    }
}
