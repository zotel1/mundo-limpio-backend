package com.mundolimpio.application.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de solicitud para cambiar el rol de un usuario.
 *
 * QUÉ HACE: Recibe el nuevo rol que se asignará al usuario target.
 * La validación de que el rol sea ADMIN u OPERATOR se hace en el
 * servicio (es una regla de negocio, no de formato).
 *
 * POR QUÉ: Usamos un record para mantener inmutabilidad y reducir
 * boilerplate. @NotBlank garantiza que el campo no llegue null
 * ni vacío antes de llegar al servicio (validación estructural).
 *
 * DIFERENCIA con AdjustmentRequest:
 *   - AdjustmentRequest valida type con @NotBlank y quantity con @NotNull.
 *   - ChangeRoleRequest solo necesita @NotBlank en newRole porque
 *     es un único campo String.
 *   - Ambos usan Jakarta Validation para validación estructural
 *     y dejan las reglas de negocio al servicio.
 *
 * @param newRole Nuevo rol a asignar (ADMIN u OPERATOR, validado en service)
 */
public record ChangeRoleRequest(
        @NotBlank(message = "El rol es obligatorio")
        String newRole
) {}
