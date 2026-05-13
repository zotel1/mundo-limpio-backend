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
 * Manejador de excepciones del módulo de gestión de usuarios.
 *
 * QUÉ HACE: Captura y maneja UserNotFoundException e
 * IllegalArgumentException lanzadas por UserManagementService,
 * devolviendo respuestas JSON estandarizadas con códigos de error
 * específicos.
 *
 * POR QUÉ:
 * - El GlobalExceptionHandler tiene handlers genéricos para
 *   IllegalArgumentException (código "BAD_REQUEST") y un catch-all
 *   que devolvería 500 para UserNotFoundException.
 * - Necesitamos códigos de error específicos: USER_NOT_FOUND (404),
 *   INVALID_ROLE (400), SELF_DEMOTION (400), INVALID_PASSWORD (400).
 * - @Order(Ordered.HIGHEST_PRECEDENCE) asegura que este handler se
 *   evalúe ANTES que el GlobalExceptionHandler.
 *
 * DIFERENCIA con AuthExceptionHandler:
 *   - AuthExceptionHandler solo captura InvalidRefreshTokenException → 401.
 *   - UserExceptionHandler captura UserNotFoundException → 404 e
 *     IllegalArgumentException → 400 con código dinámico.
 *   - Ambos usan @ControllerAdvice(basePackages = ...) para limitar
 *     alcance y @Order(HIGHEST_PRECEDENCE) para prioridad.
 *   - Ambos usan ErrorResponse record para consistencia en respuestas.
 */
@ControllerAdvice(basePackages = "com.mundolimpio.application.user")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserExceptionHandler {

    /**
     * Maneja UserNotFoundException y retorna 404 NOT_FOUND.
     *
     * QUÉ HACE: Convierte la excepción en un ErrorResponse con código
     * "USER_NOT_FOUND" y el mensaje descriptivo del error.
     *
     * @param ex      Excepción lanzada cuando un usuario no existe
     * @param request Request HTTP actual (para obtener el path)
     * @return ResponseEntity con ErrorResponse y HTTP 404
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "USER_NOT_FOUND",
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Maneja IllegalArgumentException y retorna 400 BAD_REQUEST.
     *
     * QUÉ HACE: Extrae el código de error del mensaje de la excepción.
     * Los mensajes de error en UserManagementService usan el formato
     * "CODE: descripción" (ej: "INVALID_ROLE: El rol no es válido").
     * Extraemos "CODE" como código de error para el cliente.
     *
     * POR QUÉ: En lugar de tener 3 tipos de excepción separados
     * (InvalidRoleException, SelfDemotionException, etc.), usamos
     * IllegalArgumentException con un prefijo en el mensaje. Esto
     * simplifica el código manteniendo la semántica para el cliente.
     *
     * @param ex      Excepción lanzada por validaciones de negocio
     * @param request Request HTTP actual (para obtener el path)
     * @return ResponseEntity con ErrorResponse y HTTP 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        // Extraer el código de error del prefijo del mensaje (antes de ":")
        // Ej: "INVALID_ROLE: El rol no es válido" → código "INVALID_ROLE"
        String message = ex.getMessage();
        String code = message.contains(":")
                ? message.substring(0, message.indexOf(":"))
                : "BAD_REQUEST";

        ErrorResponse errorResponse = new ErrorResponse(
                code,
                message,
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}
