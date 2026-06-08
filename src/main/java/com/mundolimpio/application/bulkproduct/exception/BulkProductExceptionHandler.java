package com.mundolimpio.application.bulkproduct.exception;

import com.mundolimpio.application.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

/**
 * Manejador global de excepciones para el módulo de BulkProduct.
* */
@ControllerAdvice(basePackages = "com.mundolimpio.application.bulkproduct")
public class BulkProductExceptionHandler {

    @ExceptionHandler(BulkProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBulkProductNotFoundException(
            BulkProductNotFoundException ex, WebRequest request) {

        ErrorResponse body = new ErrorResponse(
                "BULK_PRODUCT_NOT_FOUND",
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }
}
