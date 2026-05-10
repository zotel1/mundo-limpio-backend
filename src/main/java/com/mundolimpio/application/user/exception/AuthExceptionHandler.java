package com.mundolimpio.application.user.exception;

import com.mundolimpio.application.common.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

/**
 * Manejador de excepciones de autenticación para el módulo user.
 * <p>
 * QUÉ: Captura y maneja InvalidRefreshTokenException, devolviendo
 * una respuesta JSON estandarizada con HTTP 401 (UNAUTHORIZED).
 * POR QUÉ:
 * - El GlobalExceptionHandler tiene un catch-all Exception.class que devuelve 500.
 * - Sin este handler, InvalidRefreshTokenException caería ahí y daría 500 (incorrecto).
 * - Necesitamos 401 específicamente para refresh tokens inválidos.
 * CÓMO:
 * - @ControllerAdvice(basePackages = ...) limita el alcance al módulo user.
 * - @Order(Ordered.HIGHEST_PRECEDENCE) asegura que este handler se evalúe
 *   ANTES que el GlobalExceptionHandler (que no tiene @Order y por tanto
 *   tiene la precedencia más baja por defecto).
 * - Usamos ErrorResponse record (el mismo DTO que el GlobalExceptionHandler)
 *   para mantener consistencia en las respuestas de error de la API.
 */
@ControllerAdvice(basePackages = "com.mundolimpio.application.user")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    /**
     * Maneja InvalidRefreshTokenException y retorna 401 UNAUTHORIZED.
     * <p>
     * QUÉ: Convierte la excepción en un ErrorResponse con código
     * "INVALID_REFRESH_TOKEN" y el mensaje descriptivo del error.
     * POR QUÉ: El cliente necesita saber que debe redirigir al login
     * cuando el refresh token no es válido.
     * CÓMO: Extraemos el mensaje de la excepción y el path del request
     * para construir un ErrorResponse completo.
     *
     * @param ex      Excepción lanzada cuando el refresh token no es válido
     * @param request Request HTTP actual (para obtener el path)
     * @return ResponseEntity con ErrorResponse y HTTP 401
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
}
