package com.mundolimpio.application.common.dto;

import java.time.LocalDateTime;

/*
* Recod para estandarizar las respuestas de error en toda la API.
* Se envía cuando ocurre una excepción controlada.
* */
public record ErrorResponse(
        String code, // Código de error único (ej: PRODUCT_NOT_FOUND)
        String message, // Mensaje descriptivo del error
        LocalDateTime timestamp, // Momento en que ocurrió el error.
        String path  // Path del reques que causo el error
) {
}
