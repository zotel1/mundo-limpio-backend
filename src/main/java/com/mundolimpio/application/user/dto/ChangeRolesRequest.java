package com.mundolimpio.application.user.dto;

import com.mundolimpio.application.user.domain.Role;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * DTO de solicitud para cambiar los roles de un usuario.
 * <p>
 * WHAT: Recibe un Set de Role para asignar a un usuario como su nuevo conjunto
 * de roles. Reemplaza a ChangeRoleRequest (que aceptaba un solo String).
 * <p>
 * WHY: El modelo RBAC ahora soporta asignacion multiple de roles (UR-R2).
 * Un usuario puede ser STOCK_MANAGER y SALES_CLERK simultaneamente.
 * El Set garantiza que no haya roles duplicados en el request.
 * <p>
 * DIFFERENCES: ChangeRoleRequest tenia un campo String newRole. Este DTO
 * usa {@code Set<Role> roles} para soportar multiples roles y tipado fuerte
 * (enum en vez de String). La validacion @NotNull solo verifica que el Set
 * no sea null; reglas de negocio como "ADMIN no puede combinarse con otros
 * roles" (UR-R3) se validan en UserManagementService.
 *
 * @param roles Set de roles a asignar (no null, puede ser vacio)
 */
public record ChangeRolesRequest(
        @NotNull(message = "El conjunto de roles no puede ser nulo")
        Set<Role> roles
) {}
