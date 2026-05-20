package com.mundolimpio.application.user.dto;

/**
 * Record para la petición de registro.
 * <p>
 * WHAT: Ahora acepta email en lugar de username.
 * WHY: El frontend Flutter envía email+password. El backend autentica por email.
 * DIFFERENCES: El campo `username` fue renombrado a `email`. El tipo sigue siendo String.
 *
 * @param email    email del usuario (identificador de autenticación, único)
 * @param password contraseña (se hasheará con BCrypt)
 */
public record RegisterRequest(
        String email,
        String password
) {
}
