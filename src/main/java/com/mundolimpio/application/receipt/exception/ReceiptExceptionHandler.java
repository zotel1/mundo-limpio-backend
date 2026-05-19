package com.mundolimpio.application.receipt.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WHAT: Manejador de excepciones para el módulo receipt.
 * WHY: @ControllerAdvice(basePackages = "...receipt") limita el alcance a este módulo,
 *      siguiendo el mismo patrón que BulkProductExceptionHandler.
 *      Cada módulo maneja sus propias excepciones — esto evita que el handler global
 *      tenga que conocer todas las excepciones de todos los módulos.
 * 
 * DIFFERENCES con BulkProductExceptionHandler:
 * - Este handler usa LinkedHashMap en vez de ErrorResponse para mantener consistencia
 *   con el patrón del módulo (el handler de bulkproduct también usa Map).
 * - Agrega el campo "path" para debugging (mismo formato que GlobalExceptionHandler).
 * - Usa 422 UNPROCESSABLE_ENTITY (no estándar en Spring, pero correcto para errores de OCR).
 */
@ControllerAdvice(basePackages = "com.mundolimpio.application.receipt")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReceiptExceptionHandler {

    /**
     * Maneja OcrProcessingException → 422 Unprocessable Entity.
     * 
     * POR QUÉ 422 y no 400:
     * - 400 Bad Request = el request está mal formado (validación de formato).
     * - 422 Unprocessable Entity = el request es válido sintácticamente pero
     *   el servidor no puede procesar el contenido (ej: imagen ilegible).
     * - Es el status correcto según RFC 4918 para errores semánticos de procesamiento.
     */
    @ExceptionHandler(OcrProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleOcrProcessingException(
            OcrProcessingException ex, WebRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "OCR_PROCESSING_ERROR");
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
