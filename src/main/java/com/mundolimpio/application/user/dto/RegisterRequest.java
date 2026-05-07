package com.mundolimpio.application.user.dto;

/**
 * Record para la peticion de registro.
 *
 * @param username nombre de usuario (unico)
 * @param password contraseña (se hasheara con BCrypt)*/

public record RegisterRequest(
        String username,
        String password
) {
}
