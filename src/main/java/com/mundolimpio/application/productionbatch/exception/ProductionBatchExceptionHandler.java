package com.mundolimpio.application.productionbatch.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para el modulo de ProductionBatch.
 * Usamos @RestControllerAdvice para que funcione como @ResponseBody.
 */
@RestControllerAdvice
public class ProductionBatchExceptionHandler {

    @ExceptionHandler(ProductionBatchNotFoundException.class)
    public ResponseEntity<Map<String,Object>> handleProductionBatchNotFound(ProductionBatchNotFoundException ex) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", ex.getMessage());
        body.put("code", "PRODUCTION_BATCH_NOT_FOUND");

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }
}
