package com.mundolimpio.application.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Record que representa la solicitud de renovación del access token.
 * <p>
 * QUÉ: DTO de entrada para el endpoint POST /api/v1/auth/refresh.
 * POR QUÉ: Necesitamos validar que el cliente envíe un refresh token
 * antes de procesar la renovación. Jakarta Validation se encarga de eso.
 * CÓMO: Usamos un record por su inmutabilidad y sintaxis concisa.
 * El campo refreshToken es obligatorio — @NotBlank rechaza null, vacío o whitespace.
 *
 * @param refreshToken Token JWT de refresco (7 días de validez)
 */
public record RefreshRequest(
        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken
) {
}
