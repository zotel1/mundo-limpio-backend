package com.mundolimpio.application.common.handler;
/*
* GlobalExceptionHandler captura excepciones a nivel de aplicación
* y retorna respuestas JSON estandarizadas.
* Utiliza @RestControllerAdvice para interceptar excepciones
* en todos los controladores de la aplicación.*/

import com.mundolimpio.application.common.dto.ErrorResponse;
import com.mundolimpio.application.common.exception.ProductAlreadyExistsException;
import com.mundolimpio.application.common.exception.ProductNotFoundException;
import org.apache.coyote.Response;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones para toda la aplicación.
 *
 * POR QUÉ esta implementación:
 * - @RestControllerAdvice intercepta TODAS las excepciones lanzadas por controllers.
 *   Sin esto, cada excepción no manejada se convierte en un 500 genérico con stack trace.
 * - Cada tipo de excepción tiene su propio handler con el status HTTP correcto.
 *   Esto da respuestas RESTful: 404 para "no encontrado", 400 para "datos inválidos", etc.
 * - El último handler (Exception.class) es un catch-all que captura cualquier excepción
 *   inesperada. Debería ser el menos usado posible — cada excepción nueva debe tener su handler.
 */
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

    /**
     * Maneja IllegalArgumentException (400 - Bad Request).
     *
     * POR QUÉ este handler:
     * - SaleService lanza IllegalArgumentException cuando no hay stock suficiente.
     *   Sin este handler, caería al catch-all de Exception.class → 500 (incorrecto).
     * - IllegalArgumentException también se usa para validaciones de negocio en otros servicios.
     * - 400 es el status correcto: el cliente envió una solicitud inválida.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "BAD_REQUEST",
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja AccessDeniedException (403 - Forbidden).
     *
     * POR QUÉ este handler:
     * - @PreAuthorize("hasRole('ADMIN')") lanza esta excepción cuando el usuario no tiene el rol.
     *   Sin este handler, puede caer como 500 en algunos contextos.
     * - 403 es el status correcto: el usuario está autenticado pero NO autorizado.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "ACCESS_DENIED",
                "You do not have permission to access this resource",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Maneja OptimisticLockingFailureException (409 - Conflict).
     *
     * POR QUÉ este handler:
     * - Cuando dos ventas concurrentes intentan descontar del mismo lote,
     *   @Version detecta la colisión y lanza esta excepción.
     * - 409 Conflict le dice al cliente que debe reintentar la operación.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "CONCURRENT_MODIFICATION",
                "Concurrent modification detected. Please retry your request.",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
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
