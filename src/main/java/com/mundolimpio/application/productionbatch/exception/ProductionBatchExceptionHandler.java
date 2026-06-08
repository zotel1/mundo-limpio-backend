package com.mundolimpio.application.productionbatch.exception;

import com.mundolimpio.application.common.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

/**
 * Manejador global de excepciones para el modulo de ProductionBatch.
 * Usamos @RestControllerAdvice para que funcione como @ResponseBody.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionBatchExceptionHandler {

    @ExceptionHandler(ProductionBatchNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductionBatchNotFound(
            ProductionBatchNotFoundException ex, WebRequest request) {

        ErrorResponse body = new ErrorResponse(
                "PRODUCTION_BATCH_NOT_FOUND",
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }
}
