package com.mundolimpio.application.user.dto;

import java.time.Instant;

/**
 * DTO de respuesta para consultas de usuario.
 * <p>
 * WHAT: Agrega el campo `email` entre `username` y `role`.
 * WHY: El cliente necesita el email del usuario para display.
 * El `username` sigue siendo el display name auto-generado.
 * DIFFERENCES: Antes tenía 4 campos; ahora 5 con `email` insertado después de `username`.
 * <p>
 * NOTA: NO expone la contraseña (es información sensible).
 *
 * @param id        Identificador único del usuario
 * @param username  Nombre de usuario (display name auto-generado)
 * @param email     Email del usuario (identificador de autenticación)
 * @param role      Rol del usuario como String (ADMIN u OPERATOR)
 * @param createdAt Momento en que se creó el usuario
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String role,
        Instant createdAt
) {}
