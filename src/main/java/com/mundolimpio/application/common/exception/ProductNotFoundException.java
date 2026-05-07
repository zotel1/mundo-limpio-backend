package com.mundolimpio.application.common.exception;

/*Excepción lanzada cuando se intenta obtener un producto que no existe.
* HTTP Status: 404 NOT_FOUND
* */
public class ProductNotFoundException extends RuntimeException{
    public ProductNotFoundException(String sku) {
        super("Product with SKU '" + sku + "´not found");
    }
}
