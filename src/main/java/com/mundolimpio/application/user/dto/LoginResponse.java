package com.mundolimpio.application.user.dto;

import java.time.Instant;

/**
 * Record para la respuesta de login/registro.
 *
 * @param accessToken JWT token para acceder a endpoints protegidos
 * @param refreshToken Token para renovar el access token
 * @param role Rol del usuario (ADMIN u OPERATOR)
 * @param username Nombre de usuario
 * @param createdAt Fecha de creación del usuario
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String role,
        String username,
        Instant createdAt
) {
}
