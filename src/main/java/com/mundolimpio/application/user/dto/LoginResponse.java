package com.mundolimpio.application.user.dto;

import java.time.Instant;
import java.util.List;

/**
 * Record para la respuesta de login/registro.
 * <p>
 * WHAT: Agrega el campo `roles` (List<String>) con todos los roles del usuario
 * y mantiene el campo `role` (String) como primer rol para backward compatibility.
 * WHY: El modelo RBAC ahora soporta multiples roles (UR-R2). El cliente recibe
 * tanto la lista completa de roles como el rol legacy para transicion gradual.
 * DIFFERENCES: Antes tenia 6 campos; ahora 7 con `roles` al final.
 * El campo `role` queda como @Deprecated — el cliente debe migrar a usar `roles[0]`.
 *
 * @param accessToken  JWT token para acceder a endpoints protegidos
 * @param refreshToken token para renovar el access token
 * @param role         Primer rol como String (deprecated, usar roles)
 * @param email        Email del usuario (identificador de autenticacion)
 * @param username     Nombre de usuario (display name auto-generado)
 * @param createdAt    Fecha de creacion del usuario
 * @param roles        Lista de todos los roles del usuario como String
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        @Deprecated
        String role,
        String email,
        String username,
        Instant createdAt,
        List<String> roles
) {
}
