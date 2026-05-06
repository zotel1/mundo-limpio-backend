package com.mundolimpio.application.bulkproduct.exception;

/**
 * Excepción lanzada cuando no se encuentra una materia prima.
 */
public class BulkProductNotFoundException extends RuntimeException {
    public BulkProductNotFoundException(String message) {
        super(message);
    }
}
