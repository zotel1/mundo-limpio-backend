package com.mundolimpio.application.user.exception;

import com.mundolimpio.application.common.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Manejador de excepciones de autenticación para el módulo user.
 * <p>
 * WHAT: Captura InvalidRefreshTokenException (401 UNAUTHORIZED) y
 * ResponseStatusException con status 409 (CONFLICT, email duplicado).
 * WHY: Sin este handler, estas excepciones caerían en el catch-all
 * de GlobalExceptionHandler que devuelve 500.
 * <p>
 * DIFFERENCES: Antes solo manejaba InvalidRefreshTokenException.
 * Ahora también maneja ResponseStatusException(409) para email duplicado.
 */
@ControllerAdvice(basePackages = "com.mundolimpio.application.user")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    /**
     * Maneja InvalidRefreshTokenException y retorna 401 UNAUTHORIZED.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "INVALID_REFRESH_TOKEN",
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Maneja ResponseStatusException con status 409 (CONFLICT) para email duplicado.
     * <p>
     * WHAT: Convierte ResponseStatusException(409) en un ErrorResponse con código
     * "EMAIL_ALREADY_IN_USE" y HTTP 409.
     * WHY: AuthService.register() lanza ResponseStatusException(HttpStatus.CONFLICT, ...)
     * cuando el email ya existe. Este handler intercepta esa excepción antes de que
     * llegue al catch-all del GlobalExceptionHandler.
     *
     * @param ex      ResponseStatusException lanzada por AuthService.register()
     * @param request Request HTTP actual (para obtener el path)
     * @return ResponseEntity con ErrorResponse y HTTP 409
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(
            ResponseStatusException ex,
            WebRequest request
    ) {
        // Solo manejamos 409 CONFLICT (email duplicado). Otros status los dejamos pasar.
        if (ex.getStatusCode() != HttpStatus.CONFLICT) {
            throw ex;
        }

        ErrorResponse errorResponse = new ErrorResponse(
                "EMAIL_ALREADY_IN_USE",
                ex.getReason(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
}
