package com.mundolimpio.application.user.dto;

/**
 * Record para la petición de registro.
 *
 * @param username Nombre de usuario (único)
 * @param password Contraseña (se hasheará con BCrypt)
 */
public record RegisterRequest(
        String username,
        String password
) {
}
