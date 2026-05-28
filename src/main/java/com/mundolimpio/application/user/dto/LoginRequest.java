package com.mundolimpio.application.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Record para la petición de login.
 * <p>
 * WHAT: Ahora acepta email en lugar de username.
 * WHY: El frontend Flutter envía email+password. Spring Security autentica
 * con UsernamePasswordAuthenticationToken(email, password).
 * DIFFERENCES: El campo `username` fue renombrado a `email`.
 *
 * @param email    email del usuario (identificador de autenticación)
 * @param password contraseña
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
