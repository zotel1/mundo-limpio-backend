package com.mundolimpio.application.common.handler;
/*
* GlobalExceptionHandler captura excepciones a nivel de aplicación
* y retorna respuestas JSON estandarizadas.
* Utiliza @RestControllerAdvince para interceptar excepciones
* en todos los controladores de la aplicación.*/

import com.mundolimpio.application.common.dto.ErrorResponse;
import com.mundolimpio.application.common.exception.ProductAlreadyExistsException;
import com.mundolimpio.application.common.exception.ProductNotFoundException;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /*Maneja ProductNotFoundException (404 - Not Found)
    * Se lanza cuandio se intenta obtener un producto que no existe.*/

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(
            ProductNotFoundException ex, WebRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "PRODUCT_NOT_FOUND",
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /*
     * Maneja ProductAlreadyExistException (409 - Conflict)
     * Se lanza cuandoi se intenta crear un producto con un SKU que ya existe.
     */

    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleProductAlreadyExists(
            ProductAlreadyExistsException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "PRODUCT_ALREADY_EXISTS",
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /*
    * Maneja MethodArgumentNotValidException (400 - Bad Request)
    * Se lanza cuando las validaciones DTOs fallan (@NotBlank, @Positive, etc*/
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        // Recolectamos los errores de validacion
        String validationErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_ERROR",
                "Validation failed: " + validationErrors,
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /*
    * Maneja cualquier excepcion no controlada (500 - Internal Server Error)
    * Es un fallback para excepciones inesperadas.
    * */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error ocurred: " + ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
