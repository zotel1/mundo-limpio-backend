package com.mundolimpio.application.user.service;

import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.UserResponse;
import com.mundolimpio.application.user.exception.UserNotFoundException;
import com.mundolimpio.application.user.mapper.UserMapper;
import com.mundolimpio.application.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio principal para la gestión de usuarios por parte del ADMIN.
 *
 * QUÉ HACE: Provee operaciones administrativas CRUD-like sobre entidades
 * User: listar todos, obtener por ID, cambiar rol y resetear contraseña.
 * Está separado de AuthService (que solo maneja autenticación).
 *
 * POR QUÉ: Separamos la gestión de usuarios de la autenticación por SRP:
 * - AuthService: register, login, refresh (flujos públicos/de autenticación).
 * - UserManagementService: findAll, findById, changeRole, resetPassword
 *   (operaciones administrativas, solo ADMIN).
 * - Si mezcláramos ambas, el controlador tendría endpoints públicos y
 *   ADMIN-only mezclados, violando seguridad por capas.
 *
 * DIFERENCIA con AuthService:
 *   - AuthService usa AuthenticationManager y JwtService.
 *   - UserManagementService usa UserMapper y PasswordEncoder directamente.
 *   - AuthService maneja register con validación de duplicados.
 *   - UserManagementService maneja changeRole con autodemoción.
 *   - AuthService retorna LoginResponse (con tokens JWT).
 *   - UserManagementService retorna UserResponse (sin tokens).
 */
@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param userRepository  Repositorio JPA para operaciones CRUD de User
     * @param userMapper      Mapper para convertir User → UserResponse
     * @param passwordEncoder Encriptador BCrypt para resetear contraseñas
     */
    public UserManagementService(UserRepository userRepository,
                                 UserMapper userMapper,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    // ======================== QUERY METHODS ========================

    /**
     * Obtiene todos los usuarios del sistema.
     *
     * QUE HACE: Consulta todos los usuarios vía findAll() de JpaRepository
     * y mapea cada User a UserResponse. Nunca retorna null (si no hay
     * usuarios, retorna lista vacía).
     *
     * @return Lista de UserResponse (nunca null)
     */
    public List<UserResponse> findAll() {
        // POR QUE stream().map().toList(): convertimos la lista de entidades
        // JPA a DTOs de respuesta. toList() retorna lista inmutable.
        // Si no hay usuarios, findAll() retorna lista vacía y el stream
        // simplemente no itera → retorna lista vacía (no null).
        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .toList();
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
     * Cambia el rol de un usuario target.
     *
     * QUE HACE:
     * 1. Valida que newRole sea ADMIN u OPERATOR.
     * 2. Valida que el ADMIN autenticado no se autodesgrade.
     * 3. Busca al usuario target (lanza 404 si no existe).
     * 4. Actualiza el rol y persiste.
     *
     * POR QUE @Transactional:
     * - Aunque solo modificamos una entidad, @Transactional asegura
     *   que la operación sea atómica. Si algo falla, se revierte.
     *
     * @param targetId      ID del usuario cuyo rol cambiar
     * @param newRole       Nuevo rol como String ("ADMIN" u "OPERATOR")
     * @param currentUserId ID del ADMIN que realiza la operación
     * @return UserResponse con el nuevo rol asignado
     * @throws IllegalArgumentException si el rol es inválido o es autodemoción
     * @throws UserNotFoundException   si el usuario target no existe
     */
    @Transactional
    public UserResponse changeRole(Long targetId, String newRole, Long currentUserId) {
        // PASO 1: Validar que el nuevo rol sea ADMIN u OPERATOR
        // POR QUE validamos aquí y no en el DTO:
        // - El DTO solo valida @NotBlank (estructural).
        // - La validación de que el String corresponda a un enum Role
        //   es una regla de negocio, no de formato.
        // - Role.valueOf() lanzaría IllegalArgumentException genérica
        //   sin el código de error específico que necesita el frontend.
        if (!"ADMIN".equals(newRole) && !"OPERATOR".equals(newRole)) {
            throw new IllegalArgumentException("INVALID_ROLE: El rol '" + newRole + "' no es válido. Use ADMIN u OPERATOR.");
        }

        // PASO 2: Validar que no sea autodemoción
        // POR QUE: Un ADMIN no puede autodesgradarse a OPERATOR porque
        // perdería acceso a los endpoints de administración y nadie
        // podría revertir el cambio. Si necesita cambiar su rol, debe
        // hacerlo otro ADMIN.
        if (currentUserId.equals(targetId)) {
            throw new IllegalArgumentException(
                    "SELF_DEMOTION: No puedes cambiar tu propio rol. " +
                    "Otro administrador debe realizar esta operación."
            );
        }

        // PASO 3: Buscar el usuario target
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        // PASO 4: Actualizar el rol y persistir
        // POR QUE Role.valueOf(): convertimos el String validado al enum.
        // Como ya validamos que es "ADMIN" u "OPERATOR", esto nunca falla.
        user.setRole(Role.valueOf(newRole));
        userRepository.save(user);

        return userMapper.toResponse(user);
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
