package com.mundolimpio.application.user.dto;

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
        String email,
        String password
) {
}
