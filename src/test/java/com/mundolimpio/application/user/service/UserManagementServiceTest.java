package com.mundolimpio.application.user.service;

import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
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

        userOperator = new User("operator", "operator@mundolimpio.com", "encoded-password", Role.OPERATOR);
        userOperator.setId(2L);

        // Creamos UserResponse esperados para verify
        userAdminResponse = new UserResponse(1L, "admin", "admin@mundolimpio.com", "ADMIN", userAdmin.getCreatedAt());
        userOperatorResponse = new UserResponse(2L, "operator", "operator@mundolimpio.com", "OPERATOR", userOperator.getCreatedAt());
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
        assertEquals("ADMIN", result.get(0).role());
        assertEquals(2L, result.get(1).id());
        assertEquals("operator", result.get(1).username());
        assertEquals("OPERATOR", result.get(1).role());

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

    // ==================== CHANGE ROLE TESTS ====================

    /**
     * Test 5: changeRole con datos válidos cambia el rol y retorna UserResponse.
     *
     * QUE VERIFICA:
     * - Se valida que el nuevo rol es "ADMIN" (válido).
     * - Se valida que currentUserId != targetId (no es autodemoción).
     * - userRepository.findById() encuentra al usuario target.
     * - Se actualiza el rol del usuario a ADMIN.
     * - userRepository.save() persiste el cambio.
     * - Se retorna UserResponse con el nuevo rol.
     */
    @Test
    void changeRole_ValidRequest_ChangesRoleAndReturnsResponse() {
        // Given: un usuario OPERATOR, targetId=2, currentUserId=1
        Long targetId = 2L;
        String newRole = "ADMIN";
        Long currentUserId = 1L;

        when(userRepository.findById(targetId)).thenReturn(Optional.of(userOperator));
        when(userRepository.save(userOperator)).thenReturn(userOperator);
        when(userMapper.toResponse(userOperator)).thenReturn(
                new UserResponse(2L, "operator", "operator@mundolimpio.com", "ADMIN", userOperator.getCreatedAt())
        );

        // When: cambiamos el rol
        UserResponse result = userManagementService.changeRole(targetId, newRole, currentUserId);

        // Then: el rol debe ser ADMIN
        assertNotNull(result);
        assertEquals("ADMIN", result.role(), "El rol debe haber cambiado a ADMIN");
        assertEquals(Role.ADMIN, userOperator.getRole(), "La entidad debe tener el nuevo rol ADMIN");

        verify(userRepository).findById(targetId);
        verify(userRepository).save(userOperator);
        verify(userMapper).toResponse(userOperator);
    }

    /**
     * Test 6: changeRole intentando autodemoción lanza IllegalArgumentException.
     *
     * QUE VERIFICA:
     * - Cuando currentUserId == targetId, se lanza SELF_DEMOTION.
     * - userRepository.findById() NO es llamado (la validación es previa).
     * - userRepository.save() NO es llamado.
     */
    @Test
    void changeRole_SelfDemotion_ThrowsIllegalArgumentException() {
        // Given: targetId = currentUserId (autodemoción)
        Long targetId = 1L;
        String newRole = "OPERATOR";
        Long currentUserId = 1L;

        // When: cambiamos el rol → Then: debe lanzar excepción
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userManagementService.changeRole(targetId, newRole, currentUserId)
        );

        // Verificar que el mensaje contiene SELF_DEMOTION
        assertTrue(exception.getMessage().contains("SELF_DEMOTION"),
                "El mensaje debe indicar SELF_DEMOTION");

        // Verificar que NO se consultó ni persistió nada
        verifyNoInteractions(userRepository, userMapper);
    }

    /**
     * Test 7: changeRole con rol inválido lanza IllegalArgumentException.
     *
     * QUE VERIFICA:
     * - Cuando newRole no es ADMIN ni OPERATOR, se lanza INVALID_ROLE.
     * - userRepository.findById() NO es llamado.
     */
    @Test
    void changeRole_InvalidRole_ThrowsIllegalArgumentException() {
        // Given: un rol que no existe
        Long targetId = 2L;
        String newRole = "INVALID";
        Long currentUserId = 1L;

        // When: cambiamos el rol → Then: debe lanzar excepción
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userManagementService.changeRole(targetId, newRole, currentUserId)
        );

        // Verificar que el mensaje contiene INVALID_ROLE
        assertTrue(exception.getMessage().contains("INVALID_ROLE"),
                "El mensaje debe indicar INVALID_ROLE");

        verifyNoInteractions(userRepository, userMapper);
    }

    /**
     * Test 8: changeRole con usuario target inexistente lanza UserNotFoundException.
     *
     * QUE VERIFICA:
     * - La validación de rol pasa (rol válido).
     * - La validación de autodemoción pasa (distintos IDs).
     * - userRepository.findById() retorna Optional.empty().
     * - Se lanza UserNotFoundException.
     */
    @Test
    void changeRole_NonExistingTarget_ThrowsUserNotFoundException() {
        // Given: target que no existe
        Long targetId = 99L;
        String newRole = "ADMIN";
        Long currentUserId = 1L;

        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        // When: cambiamos el rol → Then: debe lanzar excepción
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userManagementService.changeRole(targetId, newRole, currentUserId)
        );

        assertTrue(exception.getMessage().contains(String.valueOf(targetId)));

        verify(userRepository).findById(targetId);
        verifyNoInteractions(userMapper);
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
