package com.mundolimpio.application.user.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO de respuesta para consultas de usuario.
 * <p>
 * WHAT: Agrega el campo `roles` (List<String>) con todos los roles del usuario
 * y mantiene el campo `role` (String) como primer rol para backward compatibility.
 * WHY: El modelo RBAC ahora soporta multiples roles (UR-R2). El cliente recibe
 * tanto la lista completa de roles como el rol legacy para transicion gradual.
 * DIFFERENCES: Antes tenia 5 campos; ahora 6 con `roles` al final.
 * El campo `role` queda como @Deprecated — el cliente debe migrar a usar `roles`.
 * <p>
 * NOTA: NO expone la contraseña (es informacion sensible).
 *
 * @param id        Identificador unico del usuario
 * @param username  Nombre de usuario (display name auto-generado)
 * @param email     Email del usuario (identificador de autenticacion)
 * @param role      Primer rol como String (deprecated, usar roles)
 * @param createdAt Momento en que se creo el usuario
 * @param roles     Lista de todos los roles del usuario como String
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        @Deprecated
        String role,
        Instant createdAt,
        List<String> roles
) {}
