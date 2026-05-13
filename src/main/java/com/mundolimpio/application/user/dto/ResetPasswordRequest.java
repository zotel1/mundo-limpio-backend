package com.mundolimpio.application.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de solicitud para resetear la contraseña de un usuario.
 *
 * QUÉ HACE: Recibe la nueva contraseña que el ADMIN asigna al usuario.
 * La contraseña se valida estructuralmente (no vacía, mínimo 6 caracteres)
 * y se encripta con BCrypt en el servicio antes de persistir.
 *
 * POR QUÉ: Usamos un record para mantener inmutabilidad. Las validaciones
 * con Jakarta Validation (@NotBlank, @Size) garantizan que la contraseña
 * cumple requisitos mínimos antes de llegar al servicio.
 *
 * DIFERENCIA con ChangeRoleRequest:
 *   - ChangeRoleRequest solo valida @NotBlank.
 *   - ResetPasswordRequest además valida @Size(min=6) para garantizar
 *     una contraseña mínimamente segura.
 *   - Ambos dejan la regla de negocio (encriptación, validación de
 *     existencia del usuario) al servicio.
 *
 * @param newPassword Nueva contraseña (mínimo 6 caracteres)
 */
public record ResetPasswordRequest(
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        String newPassword
) {}
