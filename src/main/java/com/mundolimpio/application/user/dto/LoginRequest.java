package com.mundolimpio.application.user.dto;

/**
 * Record para la petición de login.
 *
 * @param username Nombre de usuario
 * @param password Contraseña
 */
public record LoginRequest(
        String username,
        String password
) {
}
