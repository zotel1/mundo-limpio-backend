package com.mundolimpio.application.bulkproduct.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para el módulo de BulkProduct.
* */
@ControllerAdvice(basePackages = "com.mundolimpio.application.bulkproduct")
public class BulkProductExceptionHandler {

    @ExceptionHandler(BulkProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBulkProductNotFoundException(BulkProductNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_FOUND);
        body.put("error", "Not Found");
        body.put("message", ex.getMessage());
        body.put("code", "BULK_PRODUCT_NOT_FOUND");

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }
}
