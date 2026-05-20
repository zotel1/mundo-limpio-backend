package com.mundolimpio.application.user.service;

import com.mundolimpio.application.audit.service.AuditLogService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.ChangeRolesRequest;
import com.mundolimpio.application.user.dto.UserResponse;
import com.mundolimpio.application.user.exception.UserNotFoundException;
import com.mundolimpio.application.user.mapper.UserMapper;
import com.mundolimpio.application.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para UserManagementService usando Mockito.
 *
 * QUE HACE: Verifica el comportamiento del servicio de gestión de usuarios
 * aislado de sus dependencias (repositorio, mapper, password encoder).
 * Cada test mockea las dependencias y verifica resultados e interacciones.
 *
 * POR QUE Mockito y no @SpringBootTest:
 * - Son tests de lógica de negocio pura (no requieren BD ni contexto Spring).
 * - Mockito es más rápido: no levanta contexto Spring.
 * - Sigue el patrón exacto de AuthServiceTest e InventoryServiceTest.
 *
 * DIFERENCIA con AuthServiceTest:
 * - AuthServiceTest mockea JwtService, CustomUserDetailsService, etc.
 * - UserManagementServiceTest mockea UserRepository, UserMapper, PasswordEncoder.
 * - Ambos usan @ExtendWith(MockitoExtension.class) + @Mock + @InjectMocks.
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserManagementService userManagementService;

    private User userAdmin;
    private User userOperator;
    private UserResponse userAdminResponse;
    private UserResponse userOperatorResponse;

    /**
     * Setup común: creamos usuarios de prueba con roles distintos.
     * Se ejecuta antes de cada test para garantizar independencia.
     */
    @BeforeEach
    void setUp() {
        // Creamos User reales (no mocks) porque el servicio los modifica
        // (setRole, setPassword) y necesita objetos concretos.
        userAdmin = new User("admin", "admin@mundolimpio.com", "encoded-password", Role.ADMIN);
        userAdmin.setId(1L);

        userOperator = new User("operator", "operator@mundolimpio.com", "encoded-password", Role.SALES_CLERK);
        userOperator.setId(2L);

        // Creamos UserResponse esperados para verify
        // WHAT: Agregamos List<String> roles (6to parametro) requerido por el nuevo UserResponse multi-rol
        userAdminResponse = new UserResponse(1L, "admin", "admin@mundolimpio.com", "ADMIN", userAdmin.getCreatedAt(), List.of("ADMIN"));
        userOperatorResponse = new UserResponse(2L, "operator", "operator@mundolimpio.com", "SALES_CLERK", userOperator.getCreatedAt(), List.of("SALES_CLERK"));
    }

    // ==================== FIND ALL TESTS ====================

    /**
     * Test 1: findAll con 2 usuarios retorna lista con 2 entries.
     *
     * QUE VERIFICA:
     * - userRepository.findAll() es llamado.
     * - Cada User se mapea a UserResponse via userMapper.toResponse().
     * - La lista retornada tiene el tamaño correcto.
     */
    @Test
    void findAll_WithTwoUsers_ReturnsListWithTwoEntries() {
        // Given: dos usuarios en la BD
        List<User> users = List.of(userAdmin, userOperator);
        when(userRepository.findAll()).thenReturn(users);
        when(userMapper.toResponse(userAdmin)).thenReturn(userAdminResponse);
        when(userMapper.toResponse(userOperator)).thenReturn(userOperatorResponse);

        // When: consultamos todos los usuarios
        List<UserResponse> result = userManagementService.findAll();

        // Then: debe retornar una lista con 2 elementos
        assertNotNull(result, "La lista no debe ser null");
        assertEquals(2, result.size(), "Debe haber 2 usuarios");
        assertEquals(1L, result.get(0).id());
        assertEquals("admin", result.get(0).username());
        assertEquals("admin@mundolimpio.com", result.get(0).email());
        assertEquals("ADMIN", result.get(0).role());
        assertEquals(2L, result.get(1).id());
        assertEquals("operator", result.get(1).username());
        assertEquals("operator@mundolimpio.com", result.get(1).email());
        assertEquals("SALES_CLERK", result.get(1).role());

        verify(userRepository).findAll();
        verify(userMapper).toResponse(userAdmin);
        verify(userMapper).toResponse(userOperator);
        verifyNoMoreInteractions(userRepository, userMapper);
    }

    /**
     * Test 2: findAll sin usuarios retorna lista vacía (NO null).
     *
     * QUE VERIFICA:
     * - userRepository.findAll() retorna lista vacía.
     * - No se llama a userMapper.toResponse() (no hay nada que mapear).
     * - La lista retornada NO es null y tiene size 0.
     */
    @Test
    void findAll_WithNoUsers_ReturnsEmptyList() {
        // Given: ningún usuario en la BD
        when(userRepository.findAll()).thenReturn(List.of());

        // When: consultamos todos los usuarios
        List<UserResponse> result = userManagementService.findAll();

        // Then: debe retornar lista vacía (NO null)
        assertNotNull(result, "La lista no debe ser null cuando no hay usuarios");
        assertTrue(result.isEmpty(), "La lista debe estar vacía");

        verify(userRepository).findAll();
        verifyNoInteractions(userMapper);
    }

    // ==================== FIND BY ID TESTS ====================

    /**
     * Test 3: findById con usuario existente retorna UserResponse.
     *
     * QUE VERIFICA:
     * - userRepository.findById() es llamado con el id correcto.
     * - userMapper.toResponse() es llamado con el User encontrado.
     * - El response retornado coincide con el esperado.
     */
    @Test
    void findById_ExistingUser_ReturnsUserResponse() {
        // Given: un usuario existente con id=1
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(userAdmin));
        when(userMapper.toResponse(userAdmin)).thenReturn(userAdminResponse);

        // When: consultamos el usuario por id
        UserResponse result = userManagementService.findById(userId);

        // Then: debe retornar el response mapeado
        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("admin", result.username());
        assertEquals("admin@mundolimpio.com", result.email());
        assertEquals("ADMIN", result.role());
        assertNotNull(result.createdAt());

        verify(userRepository).findById(userId);
        verify(userMapper).toResponse(userAdmin);
        verifyNoMoreInteractions(userRepository, userMapper);
    }

    /**
     * Test 4: findById con usuario inexistente lanza UserNotFoundException.
     *
     * QUE VERIFICA:
     * - userRepository.findById() retorna Optional.empty().
     * - Se lanza UserNotFoundException con el id en el mensaje.
     * - userMapper.toResponse() NO es llamado (el flujo se corta).
     */
    @Test
    void findById_NonExistingUser_ThrowsUserNotFoundException() {
        // Given: un id que no existe
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When: consultamos → Then: debe lanzar excepción
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userManagementService.findById(userId)
        );

        // Verificar que el mensaje incluye el id
        assertTrue(exception.getMessage().contains(String.valueOf(userId)),
                "El mensaje debe contener el ID del usuario no encontrado");

        verify(userRepository).findById(userId);
        verifyNoInteractions(userMapper);
    }

    // ==================== CHANGE ROLES TESTS ====================

    /**
     * Test 5: changeRoles con datos validos asigna roles, persiste y retorna UserResponse.
     * <p>
     * WHAT: Admin asigna roles STOCK_MANAGER y SALES_CLERK a otro usuario.
     * Verifica que se reemplazan todos los roles, se persiste, se retorna
     * la respuesta mapeada y se registra auditoria.
     */
    @Test
    void changeRoles_ValidRequest_AssignsRolesAndReturnsResponse() {
        // Given: admin (id=1) asigna roles a operator (id=2)
        Long targetId = 2L;
        Long currentUserId = 1L;
        ChangeRolesRequest request = new ChangeRolesRequest(
                Set.of(Role.STOCK_MANAGER, Role.SALES_CLERK));

        when(userRepository.findById(targetId)).thenReturn(Optional.of(userOperator));
        when(userRepository.save(userOperator)).thenReturn(userOperator);
        when(userMapper.toResponse(userOperator)).thenReturn(
                new UserResponse(2L, "operator", "operator@mundolimpio.com",
                        "STOCK_MANAGER", userOperator.getCreatedAt(),
                        List.of("STOCK_MANAGER", "SALES_CLERK"))
        );

        // When: admin cambia los roles del usuario target
        UserResponse result = userManagementService.changeRoles(targetId, request, currentUserId);

        // Then: roles reemplazados, respuesta correcta, auditoria registrada
        assertNotNull(result);
        assertEquals("STOCK_MANAGER", result.role(), "role deprecated debe ser el primer rol");
        assertEquals(Set.of(Role.STOCK_MANAGER, Role.SALES_CLERK),
                userOperator.getRoles(), "La entidad debe tener los nuevos roles");
        assertEquals("operator@mundolimpio.com", result.email());

        verify(userRepository).findById(targetId);
        verify(userRepository).save(userOperator);
        verify(userMapper).toResponse(userOperator);
        // WHAT: verificar que se registro auditoria con accion ROLES_CHANGED
        verify(auditLogService).logAsync(eq(currentUserId), eq("ROLES_CHANGED"),
                eq("USER"), eq(String.valueOf(targetId)), anyString(), anyString());
    }

    /**
     * Test 6: changeRoles con ADMIN combinado con otro rol lanza IAE (UR-R3).
     * <p>
     * WHAT: ADMIN no puede combinarse con otros roles. Si el set incluye ADMIN
     * y algun otro rol, la validacion temprana lanza IllegalArgumentException
     * con codigo ADMIN_EXCLUSIVE.
     */
    @Test
    void changeRoles_AdminWithOtherRoles_ThrowsIllegalArgumentException() {
        // Given: intento de asignar ADMIN + SALES_CLERK (viola UR-R3)
        Long targetId = 2L;
        Long currentUserId = 1L;
        ChangeRolesRequest request = new ChangeRolesRequest(
                Set.of(Role.ADMIN, Role.SALES_CLERK));

        // When/Then: lanza IAE con ADMIN_EXCLUSIVE antes de tocar repositorio
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userManagementService.changeRoles(targetId, request, currentUserId)
        );

        assertTrue(exception.getMessage().contains("ADMIN_EXCLUSIVE"),
                "El mensaje debe contener ADMIN_EXCLUSIVE: " + exception.getMessage());
        verifyNoInteractions(userRepository, userMapper, auditLogService);
    }

    /**
     * Test 7: changeRoles con ADMIN solo es valido (UR-R3 triangulacion).
     * <p>
     * WHAT: Asignar solo ADMIN no viola UR-R3. Verifica que el caso borde
     * (ADMIN solo) pasa la validacion y actualiza correctamente.
     */
    @Test
    void changeRoles_AdminAlone_Succeeds() {
        // Given: asignar solo ADMIN a otro usuario
        Long targetId = 2L;
        Long currentUserId = 1L;
        ChangeRolesRequest request = new ChangeRolesRequest(Set.of(Role.ADMIN));

        when(userRepository.findById(targetId)).thenReturn(Optional.of(userOperator));
        when(userRepository.save(userOperator)).thenReturn(userOperator);
        when(userMapper.toResponse(userOperator)).thenReturn(
                new UserResponse(2L, "operator", "operator@mundolimpio.com",
                        "ADMIN", userOperator.getCreatedAt(), List.of("ADMIN"))
        );

        // When
        UserResponse result = userManagementService.changeRoles(targetId, request, currentUserId);

        // Then: ADMIN solo es valido, se asigna correctamente
        assertEquals("ADMIN", result.role());
        assertEquals(Set.of(Role.ADMIN), userOperator.getRoles());
        verify(auditLogService).logAsync(eq(currentUserId), eq("ROLES_CHANGED"),
                eq("USER"), eq(String.valueOf(targetId)), anyString(), anyString());
    }

    /**
     * Test 8: changeRoles con set vacio lanza IAE (EMPTY_ROLES).
     * <p>
     * WHAT: Al menos un rol es requerido. Set vacio es rechazado con validacion
     * temprana antes de consultar el repositorio.
     */
    @Test
    void changeRoles_EmptyRoles_ThrowsIllegalArgumentException() {
        // Given: request con roles vacio
        Long targetId = 2L;
        Long currentUserId = 1L;
        ChangeRolesRequest request = new ChangeRolesRequest(Set.of());

        // When/Then: lanza IAE con EMPTY_ROLES
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userManagementService.changeRoles(targetId, request, currentUserId)
        );

        assertTrue(exception.getMessage().contains("EMPTY_ROLES"),
                "El mensaje debe contener EMPTY_ROLES: " + exception.getMessage());
        verifyNoInteractions(userRepository, userMapper, auditLogService);
    }

    /**
     * Test 9: changeRoles con auto-remocion de ADMIN lanza IAE (UR-R6).
     * <p>
     * WHAT: Un ADMIN no puede quitarse su propio rol ADMIN. Si targetId ==
     * currentUserId y el usuario tiene ADMIN pero ADMIN no esta en el nuevo
     * set, se lanza SELF_ADMIN_REMOVAL.
     */
    @Test
    void changeRoles_SelfAdminRemoval_ThrowsIllegalArgumentException() {
        // Given: admin intenta quitarse su propio ADMIN (asignarse solo SALES_CLERK)
        Long targetId = 1L;
        Long currentUserId = 1L;
        ChangeRolesRequest request = new ChangeRolesRequest(Set.of(Role.SALES_CLERK));

        when(userRepository.findById(targetId)).thenReturn(Optional.of(userAdmin));

        // When/Then: lanza IAE con SELF_ADMIN_REMOVAL
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userManagementService.changeRoles(targetId, request, currentUserId)
        );

        assertTrue(exception.getMessage().contains("SELF_ADMIN_REMOVAL"),
                "El mensaje debe contener SELF_ADMIN_REMOVAL: " + exception.getMessage());
        // WHAT: se debe registrar auditoria incluso en intento fallido (UR-R7)
        verify(auditLogService).logAsync(eq(currentUserId), eq("ROLES_CHANGED"),
                eq("USER"), eq(String.valueOf(targetId)), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    /**
     * Test 10: changeRoles manteniendo ADMIN en self es valido (UR-R6 triangulacion).
     * <p>
     * WHAT: Un ADMIN puede reasignarse solo ADMIN (sin combinarlo con otros roles
     * por UR-R3). Verifica que cuando ADMIN permanece solo en el set, la operacion
     * es exitosa (no se bloquea por SELF_ADMIN_REMOVAL).
     */
    @Test
    void changeRoles_SelfKeepAdmin_Succeeds() {
        // Given: admin se reasigna solo ADMIN (UR-R3: ADMIN no se combina)
        Long targetId = 1L;
        Long currentUserId = 1L;
        ChangeRolesRequest request = new ChangeRolesRequest(Set.of(Role.ADMIN));

        when(userRepository.findById(targetId)).thenReturn(Optional.of(userAdmin));
        when(userRepository.save(userAdmin)).thenReturn(userAdmin);
        when(userMapper.toResponse(userAdmin)).thenReturn(userAdminResponse);

        // When
        UserResponse result = userManagementService.changeRoles(targetId, request, currentUserId);

        // Then: ADMIN solo permanece, operacion exitosa
        assertNotNull(result);
        assertEquals("ADMIN", result.role());
        assertTrue(userAdmin.getRoles().contains(Role.ADMIN),
                "ADMIN debe permanecer en los roles");
        assertEquals(1, userAdmin.getRoles().size(),
                "Solo ADMIN, sin otros roles por UR-R3");
        verify(auditLogService).logAsync(eq(currentUserId), eq("ROLES_CHANGED"),
                eq("USER"), eq(String.valueOf(targetId)), anyString(), anyString());
    }

    /**
     * Test 11: changeRoles con usuario target inexistente lanza UserNotFoundException.
     * <p>
     * WHAT: Si el usuario target no existe, se lanza UserNotFoundException
     * despues de las validaciones tempranas (roles, self-guard).
     */
    @Test
    void changeRoles_NonExistingTarget_ThrowsUserNotFoundException() {
        // Given: target que no existe en BD
        Long targetId = 99L;
        Long currentUserId = 1L;
        ChangeRolesRequest request = new ChangeRolesRequest(Set.of(Role.SALES_CLERK));

        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        // When/Then: lanza UserNotFoundException
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userManagementService.changeRoles(targetId, request, currentUserId)
        );

        assertTrue(exception.getMessage().contains(String.valueOf(targetId)));
        verify(userRepository).findById(targetId);
        verifyNoInteractions(userMapper, auditLogService);
    }

    // ==================== RESET PASSWORD TESTS ====================

    /**
     * Test 9: resetPassword con datos válidos encripta y guarda.
     *
     * QUE VERIFICA:
     * - userRepository.findById() encuentra al usuario.
     * - passwordEncoder.encode() encripta la nueva contraseña.
     * - Se actualiza la contraseña del usuario con el hash.
     * - userRepository.save() persiste el cambio.
     * - Se retorna UserResponse con los datos del usuario.
     */
    @Test
    void resetPassword_ValidRequest_EncodesAndSavesPassword() {
        // Given: un usuario existente
        Long userId = 2L;
        String newPassword = "NewPass123";
        String encodedPassword = "$2a$10$encodedHash";

        when(userRepository.findById(userId)).thenReturn(Optional.of(userOperator));
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);
        when(userRepository.save(userOperator)).thenReturn(userOperator);
        when(userMapper.toResponse(userOperator)).thenReturn(userOperatorResponse);

        // When: reseteamos la contraseña
        UserResponse result = userManagementService.resetPassword(userId, newPassword);

        // Then: la contraseña debe estar encriptada y el usuario guardado
        assertNotNull(result);
        assertEquals(2L, result.id());
        assertEquals("operator@mundolimpio.com", result.email(),
                "El email debe estar presente en la respuesta");
        assertEquals(encodedPassword, userOperator.getPassword(),
                "La contraseña debe estar encriptada con BCrypt");

        verify(userRepository).findById(userId);
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(userOperator);
        verify(userMapper).toResponse(userOperator);
    }

    /**
     * Test 10: resetPassword con usuario inexistente lanza UserNotFoundException.
     *
     * QUE VERIFICA:
     * - userRepository.findById() retorna Optional.empty().
     * - Se lanza UserNotFoundException.
     * - passwordEncoder.encode() NO es llamado.
     * - userRepository.save() NO es llamado.
     */
    @Test
    void resetPassword_NonExistingUser_ThrowsUserNotFoundException() {
        // Given: un usuario que no existe
        Long userId = 99L;
        String newPassword = "NewPass123";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When: reseteamos → Then: debe lanzar excepción
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userManagementService.resetPassword(userId, newPassword)
        );

        assertTrue(exception.getMessage().contains(String.valueOf(userId)));

        verify(userRepository).findById(userId);
        verifyNoInteractions(passwordEncoder, userMapper);
    }
}
