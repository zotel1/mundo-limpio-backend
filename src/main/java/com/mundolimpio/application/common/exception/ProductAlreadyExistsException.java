package com.mundolimpio.application.common.exception;

/*
* Excepcion lanzada cuando se intenta crear un producto con un SKU que ya existe.
* HTTP Status: 409 CONFLICT*/
public class ProductAlreadyExistsException extends RuntimeException {
    public ProductAlreadyExistsException(String sku) {
        super("Product with SKU '" + sku + "' already exists");
    }
}
