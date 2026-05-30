package com.mundolimpio.application.user.service;

import com.mundolimpio.application.audit.service.AuditLogService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.ChangeRolesRequest;
import com.mundolimpio.application.user.dto.UserResponse;
import com.mundolimpio.application.user.exception.UserNotFoundException;
import com.mundolimpio.application.user.mapper.UserMapper;
import com.mundolimpio.application.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio principal para la gestion de usuarios por parte del ADMIN.
 * <p>
 * WHAT: Provee operaciones administrativas CRUD-like sobre entidades
 * User: listar todos, obtener por ID, cambiar roles (multi-rol) y resetear contraseña.
 * Separado de AuthService (que solo maneja autenticacion).
 * <p>
 * WHY: Separamos la gestion de usuarios de la autenticacion por SRP:
 * - AuthService: register, login, refresh (flujos publicos/de autenticacion).
 * - UserManagementService: findAll, findById, changeRoles, resetPassword
 *   (operaciones administrativas, solo ADMIN).
 * <p>
 * DIFFERENCES: changeRole() reemplazado por changeRoles() que acepta Set<Role>
 * via ChangeRolesRequest. Soporta multiples roles (UR-R2), validaciones
 * ADMIN_EXCLUSIVE (UR-R3), SELF_ADMIN_REMOVAL (UR-R6), y auditoria (UR-R7).
 * El viejo metodo changeRole() permanece como @Deprecated para que el
 * controller migre sin romper compilacion.
 */
@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    /**
     * Constructor con inyeccion de dependencias.
     *
     * @param userRepository  Repositorio JPA para operaciones CRUD de User
     * @param userMapper      Mapper para convertir User → UserResponse
     * @param passwordEncoder Encriptador BCrypt para resetear contraseñas
     * @param auditLogService Servicio de auditoria asincrono para registrar cambios de rol
     */
    public UserManagementService(UserRepository userRepository,
                                 UserMapper userMapper,
                                 PasswordEncoder passwordEncoder,
                                 AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    // ======================== QUERY METHODS ========================

    /**
     * Obtiene todos los usuarios del sistema con paginación.
     *
     * @param pageable Paginación y ordenamiento (default: sort by createdAt DESC)
     * @return Página de UserResponse (nunca null)
     */
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toResponse);
    }

    /**
     * Obtiene un usuario por su ID.
     *
     * @param id ID del usuario a buscar
     * @return UserResponse con los datos del usuario
     * @throws UserNotFoundException si no existe usuario con ese ID
     */
    public UserResponse findById(Long id) {
        // POR QUE orElseThrow: si el Optional.empty(), lanzamos excepción
        // en vez de retornar null. UserExceptionHandler la capturará y
        // devolverá 404 con body JSON estandarizado.
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        return userMapper.toResponse(user);
    }

    // ======================== COMMAND METHODS ========================

    /**
     * Cambia los roles de un usuario target reemplazando todos sus roles.
     * <p>
     * WHAT: Reemplaza el Set<Role> completo del usuario con los roles
     * recibidos en el ChangeRolesRequest. Valida reglas de negocio
     * (ADMIN exclusivo, no remover propio ADMIN, roles no vacios),
     * persiste el cambio y registra auditoria asincrona (UR-R7).
     * <p>
     * WHY: El modelo RBAC multi-rol requiere reemplazo atomico del
     * conjunto de roles, no adicion/remocion individual. Las validaciones
     * garantizan UR-R3 (ADMIN no se combina) y UR-R6 (ADMIN no se quita
     * a si mismo). La auditoria cumple UR-R7 incluso en intentos fallidos.
     * <p>
     * DIFFERENCES: Reemplaza al viejo changeRole(String) que solo aceptaba
     * un rol. Ahora acepta Set<Role> tipado fuerte via ChangeRolesRequest.
     *
     * @param targetId      ID del usuario target cuyos roles cambiar
     * @param request       DTO con el Set<Role> a asignar
     * @param currentUserId ID del ADMIN que realiza la operacion
     * @return UserResponse con los nuevos roles asignados
     * @throws IllegalArgumentException si ADMIN se combina (ADMIN_EXCLUSIVE),
     *                                  si los roles estan vacios (EMPTY_ROLES),
     *                                  o si el admin se quita su propio ADMIN (SELF_ADMIN_REMOVAL)
     * @throws UserNotFoundException    si el usuario target no existe
     */
    @Transactional
    public UserResponse changeRoles(Long targetId, ChangeRolesRequest request, Long currentUserId) {
        Set<Role> newRoles = request.roles();

        // PASO 1: Validar que el set de roles no este vacio
        // WHAT: Al menos un rol es requerido (UR-R2: todo usuario debe tener al menos un rol)
        if (newRoles == null || newRoles.isEmpty()) {
            throw new IllegalArgumentException(
                    "EMPTY_ROLES: El conjunto de roles no puede estar vacio. Al menos un rol es requerido."
            );
        }

        // PASO 2: Validar ADMIN exclusividad (UR-R3)
        // WHAT: Si ADMIN esta presente, no puede haber otros roles en el set
        if (newRoles.contains(Role.ADMIN) && newRoles.size() > 1) {
            throw new IllegalArgumentException(
                    "ADMIN_EXCLUSIVE: El rol ADMIN no puede combinarse con otros roles."
            );
        }

        // PASO 3: Buscar usuario target
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        // PASO 4: Validar que el admin no se quite su propio ADMIN (UR-R6)
        // WHAT: Si el admin se esta editando a si mismo y tiene ADMIN,
        // ADMIN debe permanecer en el set. Evita lockout administrativo.
        if (currentUserId.equals(targetId)
                && user.getRoles().contains(Role.ADMIN)
                && !newRoles.contains(Role.ADMIN)) {

            // WHAT: Registrar auditoria incluso en intento fallido (UR-R7)
            // WHY: Los intentos de auto-degradacion son eventos de seguridad relevantes
            String oldRolesStr = rolesToString(user.getRoles());
            String newRolesStr = rolesToString(newRoles);
            auditLogService.logAsync(currentUserId, "ROLES_CHANGED", "USER",
                    String.valueOf(targetId), oldRolesStr, newRolesStr);

            throw new IllegalArgumentException(
                    "SELF_ADMIN_REMOVAL: No puedes quitarte tu propio rol ADMIN. " +
                    "Otro administrador debe realizar esta operacion."
            );
        }

        // PASO 5: Guardar roles viejos para auditoria antes de reemplazar
        Set<Role> oldRoles = Set.copyOf(user.getRoles());

        // PASO 6: Reemplazar roles y persistir
        // WHAT: setRoles() reemplaza el Set completo, no agrega
        user.setRoles(newRoles);
        userRepository.save(user);

        // PASO 7: Registrar auditoria asincrona (UR-R7)
        // WHAT: logAsync() usa @Async + REQUIRES_NEW → no bloquea y sobrevive rollback
        auditLogService.logAsync(currentUserId, "ROLES_CHANGED", "USER",
                String.valueOf(targetId), rolesToString(oldRoles), rolesToString(newRoles));

        return userMapper.toResponse(user);
    }

    /**
     * Cambia el rol de un usuario target (LEGACY — usar changeRoles).
     *
     * @deprecated Usar {@link #changeRoles(Long, ChangeRolesRequest, Long)} para multi-rol.
     *             Este metodo se mantiene solo para que el controller pueda migrar sin
     *             romper compilacion. Sera removido cuando el controller migre a /{id}/roles.
     */
    @Deprecated
    @Transactional
    public UserResponse changeRole(Long targetId, String newRole, Long currentUserId) {
        // PASO 1: Validar que el nuevo rol sea ADMIN u OPERATOR
        if (!"ADMIN".equals(newRole) && !"OPERATOR".equals(newRole)) {
            // WHAT: OPERATOR ya no existe como enum pero este metodo legacy aun lo acepta
            // para no romper la API vieja durante la transicion
            if (!"SALES_CLERK".equals(newRole) && !"STOCK_MANAGER".equals(newRole)
                    && !"STOCK_OPERATOR".equals(newRole) && !"PRODUCTION_OP".equals(newRole)
                    && !"ACCOUNTANT".equals(newRole)) {
                throw new IllegalArgumentException("INVALID_ROLE: El rol '" + newRole + "' no es valido.");
            }
        }

        // PASO 2: Validar que no sea autodemocion (legacy: self-demotion bloquea cualquier cambio propio)
        if (currentUserId.equals(targetId)) {
            throw new IllegalArgumentException(
                    "SELF_DEMOTION: No puedes cambiar tu propio rol. " +
                    "Otro administrador debe realizar esta operacion."
            );
        }

        // PASO 3: Buscar el usuario target
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        // PASO 4: Actualizar el rol y persistir
        // WHAT: Mapea OPERATOR→SALES_CLERK para backward compat
        String mappedRole = "OPERATOR".equals(newRole) ? "SALES_CLERK" : newRole;
        user.setRole(Role.valueOf(mappedRole));
        userRepository.save(user);

        return userMapper.toResponse(user);
    }

    /**
     * Convierte un Set<Role> a String legible para auditoria.
     * <p>
     * WHAT: Formato "[ADMIN, STOCK_MANAGER]" para el campo old_value/new_value
     * de la tabla audit_log. Ordenado alfabeticamente para consistencia.
     */
    private String rolesToString(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return "[]";
        }
        return roles.stream()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Resetea la contraseña de un usuario (solo ADMIN).
     *
     * QUE HACE:
     * 1. Busca al usuario por ID (lanza 404 si no existe).
     * 2. Encripta la nueva contraseña con BCrypt.
     * 3. Actualiza la contraseña y persiste.
     *
     * POR QUE usamos PasswordEncoder.encode():
     * - Nunca guardamos contraseñas en texto plano.
     * - BCrypt es el algoritmo configurado en SecurityConfig.
     * - El mismo encoder que usa AuthService para register.
     *
     * DIFERENCIA con register() en AuthService:
     *   - register() crea un usuario nuevo con contraseña encriptada.
     *   - resetPassword() actualiza la contraseña de un usuario existente.
     *   - Ambos usan el mismo PasswordEncoder bean para consistencia.
     *
     * @param id          ID del usuario cuya contraseña resetear
     * @param newPassword Nueva contraseña en texto plano (se encripta)
     * @return UserResponse con los datos del usuario (sin contraseña)
     * @throws UserNotFoundException si no existe usuario con ese ID
     */
    @Transactional
    public UserResponse resetPassword(Long id, String newPassword) {
        // PASO 1: Buscar el usuario
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        // PASO 2: Encriptar la nueva contraseña con BCrypt
        // POR QUE encode(): genera un hash con salt incorporado.
        // Cada llamada produce un hash diferente aunque la contraseña
        // sea la misma (BCrypt genera salt aleatorio automáticamente).
        String encodedPassword = passwordEncoder.encode(newPassword);

        // PASO 3: Actualizar y persistir
        user.setPassword(encodedPassword);
        userRepository.save(user);

        return userMapper.toResponse(user);
    }
}
