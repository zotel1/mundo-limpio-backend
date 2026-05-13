package com.mundolimpio.application.user.controller;

import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.ChangeRoleRequest;
import com.mundolimpio.application.user.dto.ResetPasswordRequest;
import com.mundolimpio.application.user.dto.UserResponse;
import com.mundolimpio.application.user.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de usuarios (ADMIN only).
 *
 * QUÉ HACE: Expone endpoints ADMIN para listar, consultar, cambiar rol
 * y resetear contraseña de usuarios del sistema. Separado de AuthController
 * (que solo maneja autenticación pública) para mantener SRP.
 *
 * POR QUÉ seguimos el patrón de InventoryController:
 * - @PreAuthorize("hasRole('ADMIN')") en cada método (no a nivel de clase)
 *   para mantener flexibilidad si en el futuro algún endpoint necesita
 *   un rol diferente.
 * - Constructor injection (no @Autowired en campos).
 * - Swagger/OpenAPI annotations para documentación automática.
 *
 * DIFERENCIA con AuthController:
 * - AuthController tiene endpoints públicos (permitAll en SecurityConfig).
 * - UserManagementController requiere ADMIN en TODOS los endpoints.
 * - AuthController trabaja con LoginResponse (incluye tokens JWT).
 * - UserManagementController trabaja con UserResponse (sin tokens).
 *
 * DIFERENCIA con InventoryController:
 * - InventoryController tiene GET (consulta) y POST (ajuste).
 * - UserManagementController tiene GET (listar/detalle) y PATCH (rol/password).
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "Endpoints ADMIN para gestión de usuarios")
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
     * Obtiene todos los usuarios del sistema.
     *
     * QUÉ HACE: Retorna la lista completa de usuarios registrados.
     * Cada usuario incluye id, username, role y createdAt (sin contraseña).
     * Si no hay usuarios, retorna lista vacía (no null).
     *
     * CÓMO FUNCIONA EL FLUJO:
     * 1. @PreAuthorize verifica que el usuario tenga ROLE_ADMIN.
     * 2. UserManagementService.findAll() consulta todos los usuarios.
     *
     * @return 200 OK con lista de UserResponse (puede ser vacía)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "List all users",
            description = "Returns all registered users. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of users (may be empty)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: only ADMIN can access")
    })
    public ResponseEntity<List<UserResponse>> findAll() {
        List<UserResponse> users = userManagementService.findAll();
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

    // ========================= CHANGE ROLE =========================

    /**
     * Cambia el rol de un usuario.
     *
     * QUÉ HACE: Asigna un nuevo rol (ADMIN u OPERATOR) a un usuario target.
     * Valida que el ADMIN autenticado no se autodesgrade (extrae su ID
     * del SecurityContextHolder y lo pasa al servicio para comparación).
     *
     * CÓMO FUNCIONA EL FLUJO:
     * 1. @Valid valida ChangeRoleRequest: newRole no vacío.
     * 2. @PreAuthorize verifica ROLE_ADMIN.
     * 3. getCurrentUserId() extrae el ID del ADMIN del SecurityContext.
     * 4. UserManagementService.changeRole() ejecuta validaciones de negocio.
     * 5. Si el rol es inválido → IllegalArgumentException → 400 INVALID_ROLE.
     * 6. Si es autodemoción → IllegalArgumentException → 400 SELF_DEMOTION.
     * 7. Si el usuario target no existe → UserNotFoundException → 404.
     *
     * POR QUÉ extraemos currentUserId en el controller y no en el service:
     * - El service no debería depender de SecurityContextHolder (acoplamiento).
     * - El controller es la capa que conoce el contexto HTTP/seguridad.
     * - El service recibe el ID como parámetro y se mantiene testeable.
     *
     * @param id      ID del usuario cuyo rol cambiar
     * @param request Body con newRole (ADMIN u OPERATOR)
     * @return 200 OK con UserResponse actualizado
     */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Change user role",
            description = "Changes the role of a user (ADMIN or OPERATOR). " +
                    "Prevents self-demotion. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid role or self-demotion"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: only ADMIN can access"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        // Extraer el ID del ADMIN autenticado para la guardia de autodemoción
        Long currentUserId = getCurrentUserId();
        UserResponse response = userManagementService.changeRole(id, request.newRole(), currentUserId);
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
