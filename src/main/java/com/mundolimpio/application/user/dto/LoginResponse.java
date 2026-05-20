package com.mundolimpio.application.user.dto;

import java.time.Instant;

/**
 * Record para la respuesta de login/registro.
 * <p>
 * WHAT: Agrega el campo `email` entre `role` y `username`.
 * WHY: El cliente necesita saber el email usado para autenticación.
 * El `username` se preserva como display name para backward compatibility.
 * DIFFERENCES: Antes tenía 5 campos; ahora 6 con `email` insertado después de `role`.
 *
 * @param accessToken  JWT token para acceder a endpoints protegidos
 * @param refreshToken token para renovar el access token
 * @param role         Rol del usuario (ADMIN u OPERATOR)
 * @param email        Email del usuario (identificador de autenticación)
 * @param username     Nombre de usuario (display name auto-generado)
 * @param createdAt    Fecha de creación del usuario
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String role,
        String email,
        String username,
        Instant createdAt
) {
}
