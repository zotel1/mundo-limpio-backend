package com.mundolimpio.application.productionbatch.exception;

/**
 * Excepción lanzada cuando no se encuentra un lote de producción.
 */
public class ProductionBatchNotFoundException extends RuntimeException {
    public ProductionBatchNotFoundException(String message) {
        super(message);
    }
}
