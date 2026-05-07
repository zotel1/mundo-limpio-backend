package com.mundolimpio.application.user.dto;

/**
 * Record para la peticion de login.
 *
 * @param username Nombre de usuario
 * @param password contraseña*/
public record LoginRequest(
        String username,
        String password
) {
}
