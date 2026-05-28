package com.mundolimpio.application.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Record para la petición de registro.
 * <p>
 * WHAT: Ahora acepta email en lugar de username. Incluye validaciones Jakarta
 * para asegurar que email y password cumplen requisitos mínimos.
 * WHY: El frontend Flutter envía email+password. Las validaciones Jakarta
 * producen HTTP 400 con mensaje descriptivo si los datos son inválidos.
 * DIFFERENCES: Antes no tenía validaciones — cualquier string pasaba.
 * Ahora email requiere @NotBlank @Email @Size(min=5, max=100) y password
 * @NotBlank @Size(min=6, max=100).
 *
 * @param email    email del usuario (identificador de autenticación, único)
 * @param password contraseña (se hasheará con BCrypt)
 */
public record RegisterRequest(
        @NotBlank @Email @Size(min = 5, max = 100) String email,
        @NotBlank @Size(min = 6, max = 100) String password
) {
}
