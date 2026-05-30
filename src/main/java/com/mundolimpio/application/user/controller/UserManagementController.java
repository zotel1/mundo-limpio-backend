package com.mundolimpio.application.user.controller;

import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.ChangeRolesRequest;
import com.mundolimpio.application.user.dto.ResetPasswordRequest;
import com.mundolimpio.application.user.dto.UserResponse;
import com.mundolimpio.application.user.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para la gestion de usuarios (ADMIN only).
 * <p>
 * WHAT: Expone endpoints ADMIN para listar, consultar, cambiar roles
 * (multi-rol via PATCH /{id}/roles) y resetear contraseña de usuarios.
 * Separado de AuthController (autenticacion publica) para SRP.
 * <p>
 * WHY: El modelo RBAC multi-rol requiere un endpoint que acepte Set<Role>
 * en vez de un solo String. El viejo PATCH /{id}/role queda como @Deprecated
 * para backward compatibility.
 * <p>
 * DIFFERENCES: Antes solo existia PATCH /{id}/role con String newRole.
 * Ahora el endpoint principal es PATCH /{id}/roles con ChangeRolesRequest
 * que acepta array JSON de roles (UR-R2).
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "Endpoints ADMIN para gestion de usuarios")
public class UserManagementController {

    private final UserManagementService userManagementService;

    /**
     * Constructor con inyección de dependencias.
     * Spring inyecta UserManagementService automáticamente.
     */
    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    // ========================= LIST ALL USERS =========================

    /**
     * Obtiene todos los usuarios del sistema con paginación.
     *
     * @param pageable Paginación y ordenamiento (default: sort by createdAt DESC)
     * @return 200 OK con página de UserResponse (puede ser vacía)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "List all users",
            description = "Returns all registered users with pagination. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of users (may be empty)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: only ADMIN can access")
    })
    public ResponseEntity<Page<UserResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UserResponse> users = userManagementService.findAll(pageable);
        return ResponseEntity.ok(users);
    }

    // ========================= GET USER BY ID =========================

    /**
     * Obtiene un usuario por su ID.
     *
     * QUÉ HACE: Retorna los detalles de un usuario específico.
     * Si el ID no existe, retorna 404 USER_NOT_FOUND.
     *
     * @param id ID del usuario a consultar
     * @return 200 OK con UserResponse, o 404 si no existe
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get user by ID",
            description = "Returns the details of a specific user. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: only ADMIN can access"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        UserResponse response = userManagementService.findById(id);
        return ResponseEntity.ok(response);
    }

    // ========================= CHANGE ROLES (MULTI-ROLE) =========================

    /**
     * Cambia los roles de un usuario (multi-rol).
     * <p>
     * WHAT: Reemplaza todos los roles del usuario target con el Set<Role>
     * recibido en ChangeRolesRequest. Soporta asignacion multiple de roles
     * (UR-R2). Valida ADMIN_EXCLUSIVE (UR-R3), EMPTY_ROLES, y
     * SELF_ADMIN_REMOVAL (UR-R6). Registra auditoria ROLES_CHANGED (UR-R7).
     * <p>
     * WHY: Reemplaza al viejo PATCH /{id}/role que solo aceptaba un String.
     * El nuevo endpoint acepta el Set completo de roles como array JSON.
     * <p>
     * DIFFERENCES: Antes PATCH /{id}/role con body {"newRole": "ADMIN"}.
     * Ahora PATCH /{id}/roles con body {"roles": ["STOCK_MANAGER","SALES_CLERK"]}.
     *
     * @param id      ID del usuario cuyos roles cambiar
     * @param request Body con Set<Role> a asignar
     * @return 200 OK con UserResponse actualizado
     */
    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Change user roles (multi-role)",
            description = "Replaces all roles of a user with the provided set. " +
                    "ADMIN cannot be combined with other roles. " +
                    "An admin cannot remove their own ADMIN role. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles changed successfully"),
            @ApiResponse(responseCode = "400", description = "ADMIN_EXCLUSIVE, EMPTY_ROLES, or SELF_ADMIN_REMOVAL"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: only ADMIN can access"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> changeRoles(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRolesRequest request) {
        // Extraer el ID del ADMIN autenticado para validaciones de autodemoción
        Long currentUserId = getCurrentUserId();
        UserResponse response = userManagementService.changeRoles(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    // ========================= RESET PASSWORD =========================

    /**
     * Resetea la contraseña de un usuario.
     *
     * QUÉ HACE: Asigna una nueva contraseña (encriptada con BCrypt)
     * a un usuario específico. Solo accessible por ADMIN.
     *
     * CÓMO FUNCIONA EL FLUJO:
     * 1. @Valid valida ResetPasswordRequest: newPassword no vacío, min 6 chars.
     * 2. @PreAuthorize verifica ROLE_ADMIN.
     * 3. UserManagementService.resetPassword() encripta y persiste.
     * 4. Si el usuario no existe → UserNotFoundException → 404.
     *
     * @param id      ID del usuario cuya contraseña resetear
     * @param request Body con newPassword (mínimo 6 caracteres)
     * @return 200 OK con UserResponse (sin contraseña)
     */
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Reset user password",
            description = "Resets the password of a user (BCrypt encrypted). " +
                    "Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password: validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: only ADMIN can access"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        UserResponse response = userManagementService.resetPassword(id, request.newPassword());
        return ResponseEntity.ok(response);
    }

    // ========================= HELPERS =========================

    /**
     * Extrae el ID del usuario autenticado desde el SecurityContextHolder.
     *
     * QUÉ HACE: Obtiene el Authentication del SecurityContext y verifica
     * si el principal es una instancia de User (entidad de dominio).
     * En producción, JwtAuthenticationFilter setea la entidad User como
     * principal (porque CustomUserDetailsService.loadUserByUsername()
     * retorna un User que implementa UserDetails).
     *
     * POR QUÉ esta implementación:
     * - El principal es la entidad User (dominio) porque el JWT filter
     *   setea el UserDetails retornado por loadUserByUsername().
     * - User tiene getter getId() que no existe en UserDetails genérico.
     * - instanceof User garantiza que podemos castear y llamar getId().
     *
     * @return ID del usuario autenticado, o null si no se puede determinar
     */
    private Long getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }
}
