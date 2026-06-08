package com.mundolimpio.application.user.service;

import com.mundolimpio.application.security.service.CustomUserDetailsService;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.LoginRequest;
import com.mundolimpio.application.user.dto.LoginResponse;
import com.mundolimpio.application.user.dto.RefreshRequest;
import com.mundolimpio.application.user.dto.RegisterRequest;
import com.mundolimpio.application.user.exception.InvalidRefreshTokenException;
import com.mundolimpio.application.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test para AuthService.refresh().
 *
 * POR QUÉ Mockito y no Spring Boot Test:
 * - AuthService tiene dependencias con JwtService, CustomUserDetailsService, etc.
 * - Queremos probar LA LÓGICA del método refresh() de forma aislada,
 *   sin levantar Spring, sin DB, sin JWT real.
 * - Mockito nos permite simular cada escenario (token válido, expirado, mal formado, etc.)
 *   de forma rápida y determínistica.
 *
 * CÓMO FUNCIONA:
 * - @ExtendWith(MockitoExtension.class): Inicializa los mocks automáticamente.
 * - @Mock: Crea simulaciones de cada dependencia.
 * - @InjectMocks: Crea AuthService e inyecta los mocks en su constructor.
 *
 * QUÉ TESTEAMOS:
 * - refresh() con token válido → nuevo par de tokens + datos del usuario
 * - refresh() con token mal formado → InvalidRefreshTokenException(MALFORMED)
 * - refresh() con usuario eliminado → InvalidRefreshTokenException(USER_NOT_FOUND)
 * - refresh() con token expirado/inválido → InvalidRefreshTokenException(INVALID)
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    /**
     * Setup común: creamos un usuario de prueba con role OPERATOR.
     * Se ejecuta antes de cada test para garantizar independencia.
     */
    @BeforeEach
    void setUp() {
        // Creamos un User real (no mock) porque refresh() castea UserDetails a User
        // para obtener getRole() y getCreatedAt(). Un mock de UserDetails no funcionaría.
        testUser = new User("testuser", "testuser@mundolimpio.com", "encoded-password", Role.SALES_CLERK);
    }

    // ==================== TEST 1: Token válido → nuevo par ====================

    /**
     * Test 1: refresh() con token válido devuelve un nuevo par de tokens.
     *
     * QUÉ VERIFICA:
     * - jwtService.extractUsername() extrae el usuario correctamente
     * - customUserDetailsService.loadUserByUsername() carga al usuario
     * - jwtService.isTokenValid() confirma que el token es válido
     * - Se genera un nuevo access token y un nuevo refresh token
     * - La respuesta incluye role, username, y createdAt correctos
     */
    @Test
    void shouldReturnNewTokensWhenRefreshTokenIsValid() {
        // Given: configuramos los mocks para un escenario exitoso
        RefreshRequest request = new RefreshRequest("valid-refresh-token");

        when(jwtService.extractUsername("valid-refresh-token")).thenReturn("testuser");
        when(customUserDetailsService.loadUserByUsername("testuser")).thenReturn(testUser);
        when(jwtService.isTokenValid("valid-refresh-token", testUser)).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("new-access-token");
        when(jwtService.generateToken(eq(testUser), anyLong())).thenReturn("new-refresh-token");

        // When: ejecutamos refresh()
        LoginResponse response = authService.refresh(request);

        // Then: verificamos que la respuesta contiene los nuevos tokens y datos del usuario
        assertNotNull(response);
        assertEquals("new-access-token", response.accessToken(),
                "El access token debe ser el nuevo generado");
        assertEquals("new-refresh-token", response.refreshToken(),
                "El refresh token debe ser el nuevo generado");
        assertEquals("SALES_CLERK", response.role(),
                "El rol debe coincidir con el del usuario");
        assertEquals("testuser", response.username(),
                "El username debe ser el raw username (display name): " + response.username());
        assertEquals("testuser@mundolimpio.com", response.email(),
                "El email debe coincidir con el email del usuario");
        assertNotNull(response.createdAt(),
                "La fecha de creación no debe ser nula");
        // WHAT: Verifica que el campo roles (multi-rol) viene poblado
        assertNotNull(response.roles(), "roles no debe ser null");
        assertTrue(response.roles().contains("SALES_CLERK"),
                "roles debe contener SALES_CLERK: " + response.roles());

        // Verificamos que se llamaron TODAS las dependencias en el orden correcto
        verify(jwtService).extractUsername("valid-refresh-token");
        verify(customUserDetailsService).loadUserByUsername("testuser");
        verify(jwtService).isTokenValid("valid-refresh-token", testUser);
        verify(jwtService).generateToken(testUser);
        verify(jwtService).generateToken(eq(testUser), anyLong());
    }

    // ==================== TEST 2: Token mal formado → MALFORMED ====================

    /**
     * Test 2: refresh() con token mal formado lanza InvalidRefreshTokenException(MALFORMED).
     *
     * QUÉ VERIFICA:
     * - jwtService.extractUsername() lanza JwtException cuando el token no es un JWT válido
     * - AuthService.refresh() captura JwtException y lanza InvalidRefreshTokenException
     * - El error tiene reason = MALFORMED y mensaje descriptivo en español
     *
     * POR QUÉ este escenario:
     * - El cliente Flutter podría enviar un token corrupto o en formato incorrecto.
     * - Necesitamos un mensaje claro: "El refresh token está mal formado".
     */
    @Test
    void shouldThrowMalformedWhenTokenIsMalformed() {
        // Given: un token que no es un JWT válido
        RefreshRequest request = new RefreshRequest("bad-token");

        when(jwtService.extractUsername("bad-token"))
                .thenThrow(new JwtException("JWT string is malformed"));

        // When: ejecutamos refresh() → debe lanzar excepción
        InvalidRefreshTokenException exception = assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(request)
        );

        // Then: la excepción debe ser MALFORMED con mensaje en español
        assertEquals(InvalidRefreshTokenException.RefreshError.MALFORMED,
                exception.getReason(),
                "Token mal formado debe dar error MALFORMED");
        assertTrue(exception.getMessage().contains("mal formado"),
                "El mensaje debe indicar que el token está mal formado: " + exception.getMessage());
    }

    // ==================== TEST 3: Usuario eliminado → USER_NOT_FOUND ====================

    /**
     * Test 3: refresh() con usuario eliminado lanza InvalidRefreshTokenException(USER_NOT_FOUND).
     *
     * QUÉ VERIFICA:
     * - El token es válido estructuralmente (extractUsername funciona)
     * - Pero el usuario fue eliminado de la DB (loadUserByUsername lanza UsernameNotFoundException)
     * - AuthService.refresh() captura UsernameNotFoundException y lanza InvalidRefreshTokenException
     * - El error tiene reason = USER_NOT_FOUND
     *
     * POR QUÉ este escenario:
     * - Un usuario puede ser eliminado mientras su refresh token sigue vigente.
     * - El cliente necesita saber que debe redirigir al login (el usuario ya no existe).
     */
    @Test
    void shouldThrowUserNotFoundWhenUserIsDeleted() {
        // Given: un token de un usuario que ya no existe en la DB
        RefreshRequest request = new RefreshRequest("token");

        when(jwtService.extractUsername("token")).thenReturn("deleteduser");
        when(customUserDetailsService.loadUserByUsername("deleteduser"))
                .thenThrow(new UsernameNotFoundException("User not found: deleteduser"));

        // When: ejecutamos refresh() → debe lanzar excepción
        InvalidRefreshTokenException exception = assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(request)
        );

        // Then: la excepción debe ser USER_NOT_FOUND
        assertEquals(InvalidRefreshTokenException.RefreshError.USER_NOT_FOUND,
                exception.getReason(),
                "Usuario eliminado debe dar error USER_NOT_FOUND");
    }

    // ==================== TEST 4: Token expirado/inválido → INVALID ====================

    /**
     * Test 4: refresh() con token expirado lanza InvalidRefreshTokenException(INVALID).
     *
     * QUÉ VERIFICA:
     * - El token es estructuralmente válido (extractUsername funciona)
     * - El usuario existe (loadUserByUsername carga correctamente)
     * - Pero isTokenValid() devuelve false (token expirado o no pertenece al usuario)
     * - AuthService.refresh() lanza InvalidRefreshTokenException con reason = INVALID
     *
     * POR QUÉ este escenario:
     * - Es el caso más común: el refresh token expiró (7 días de vida).
     * - También cubre tokens que fueron revocados o no pertenecen al usuario.
     * - El mensaje "no es válido o ha expirado" es claro para el usuario.
     */
    @Test
    void shouldThrowInvalidWhenTokenIsExpired() {
        // Given: un token expirado (o inválido para este usuario)
        RefreshRequest request = new RefreshRequest("expired-token");

        when(jwtService.extractUsername("expired-token")).thenReturn("testuser");
        when(customUserDetailsService.loadUserByUsername("testuser")).thenReturn(testUser);
        when(jwtService.isTokenValid("expired-token", testUser)).thenReturn(false);

        // When: ejecutamos refresh() → debe lanzar excepción
        InvalidRefreshTokenException exception = assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(request)
        );

        // Then: la excepción debe ser INVALID con mensaje en español
        assertEquals(InvalidRefreshTokenException.RefreshError.INVALID,
                exception.getReason(),
                "Token expirado debe dar error INVALID");
        assertTrue(exception.getMessage().contains("no es válido"),
                "El mensaje debe indicar que el token no es válido o expiró: " + exception.getMessage());
    }

    // ==================== TEST 5: Registro exitoso con email ====================

    /**
     * Test 5: register() con email válido devuelve LoginResponse con email + username.
     *
     * WHAT: Verifica que register() acepta email+password, persiste el usuario
     * con username auto-generado, y devuelve LoginResponse incluyendo el campo email.
     *
     * WHY: El frontend Flutter envía email en RegisterRequest. El backend debe
     * aceptar email como identificador primario y devolver ambos (email + username).
     */
    @Test
    void register_withEmail_ReturnsLoginResponse() {
        // Given: RegisterRequest con email (nuevo contrato)
        RegisterRequest request = new RegisterRequest("test@mail.com", "password123");

        when(userRepository.existsByEmail("test@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        // El username auto-generado será "test" (prefijo del email)
        when(userRepository.existsByUsername("test")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            // Simulamos que JPA asigna ID
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateToken(any(User.class), anyLong())).thenReturn("refresh-token");

        // When
        LoginResponse response = authService.register(request);

        // Then: La respuesta contiene email + username
        assertNotNull(response, "La respuesta no debe ser nula");
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        // WHAT: register() asigna CUSTOMER como rol por defecto
        assertEquals("CUSTOMER", response.role(),
                "register() debe asignar role=CUSTOMER: " + response.role());
        assertEquals("test@mail.com", response.email(),
                "El campo email debe contener el email del usuario");
        assertEquals("test", response.username(),
                "El username debe ser el prefijo auto-generado desde el email");
        assertNotNull(response.createdAt());
        // WHAT: register() ahora asigna Role.CUSTOMER como default
        assertNotNull(response.roles(), "roles no debe ser null");
        assertTrue(response.roles().contains("CUSTOMER"),
                "register() debe crear usuario con roles=[CUSTOMER]: " + response.roles());

        // Verificar que se usaron las dependencias correctas
        verify(userRepository).existsByEmail("test@mail.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).existsByUsername("test");
        verify(userRepository).save(any(User.class));
        verify(jwtService).generateToken(any(User.class));
        verify(jwtService).generateToken(any(User.class), anyLong());
    }

    // ==================== TEST 6: Email duplicado → 409 ====================

    /**
     * Test 6: register() lanza ResponseStatusException 409 cuando el email ya existe.
     *
     * WHAT: Verifica que el sistema rechaza registros duplicados por email.
     * WHY: El email es el identificador único de autenticación. Dos usuarios
     * no pueden compartir el mismo email.
     */
    @Test
    void register_duplicateEmail_Throws409() {
        // Given: un email que ya está registrado
        RegisterRequest request = new RegisterRequest("existing@mail.com", "password123");

        when(userRepository.existsByEmail("existing@mail.com")).thenReturn(true);

        // When: intentamos registrar con el mismo email
        // Then: debe lanzar ResponseStatusException con 409 CONFLICT
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertTrue(exception.getMessage().contains("Email already in use"),
                "El mensaje debe indicar que el email ya está en uso: " + exception.getMessage());

        // Verificar que NO se persiste nada
        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateToken(any(User.class));
    }

    // ==================== TEST 7: Username auto-generado ====================

    /**
     * Test 7: register() auto-genera username desde el prefijo del email.
     *
     * WHAT: Verifica que el username se genera automáticamente como el prefijo
     * del email (antes del @), y que en caso de colisión se agrega un sufijo.
     * WHY: El username es necesario para display/admin pero el usuario solo
     * provee email. El sistema debe generarlo sin intervención humana.
     */
    @Test
    void register_autoGeneratesUsername() {
        // Given: email "juan@mail.com" → username base "juan"
        // Pero "juan" ya existe → debe generar "juan-xxxx" (con sufijo aleatorio)
        RegisterRequest request = new RegisterRequest("juan@mail.com", "password123");

        when(userRepository.existsByEmail("juan@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        // Simulamos colisión: "juan" ya existe (primer existsByUsername),
        // pero el sufijo aleatorio no (segundo existsByUsername).
        // Usamos thenReturn(true, false): true para el prefijo, false para el candidato aleatorio
        when(userRepository.existsByUsername(anyString())).thenReturn(true, false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateToken(any(User.class), anyLong())).thenReturn("refresh-token");

        // When
        LoginResponse response = authService.register(request);

        // Then: el username NO es "juan" (hubo colisión, se agregó sufijo)
        assertNotNull(response);
        assertNotEquals("juan", response.username(),
                "El username NO debe ser 'juan' porque ya existe — debe tener sufijo");
        assertTrue(response.username().startsWith("juan-"),
                "El username debe empezar con 'juan-' (prefijo + guión): " + response.username());
        assertEquals("juan@mail.com", response.email(),
                "El email debe ser el provisto en el request");
    }

    // ==================== TEST 9: Login con usuario eliminado después de auth ====================

    /**
     * Test 9: login() lanza UsernameNotFoundException cuando findByEmail() retorna empty.
     *
     * WHAT: Verifica que si authenticationManager.authenticate() pasa (credenciales OK)
     * pero el usuario fue eliminado entre la autenticación y la búsqueda por email,
     * se lanza UsernameNotFoundException (no RuntimeException).
     * WHY: UsernameNotFoundException extiende AuthenticationException y será capturado
     * por el handler de AuthenticationException en GlobalExceptionHandler → 401
     * (consistente con el flujo de credenciales inválidas).
     */
    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserNotFoundAfterAuth() {
        // Given: autenticación pasa pero el usuario ya no existe en la DB
        LoginRequest request = new LoginRequest("test@mail.com", "password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("test@mail.com", "password123"));
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.empty());

        // When: login() → debe lanzar UsernameNotFoundException
        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> authService.login(request)
        );

        // Then: el mensaje debe contener el email del usuario
        assertTrue(exception.getMessage().contains("test@mail.com"),
                "El mensaje debe contener el email: " + exception.getMessage());
        assertFalse(exception.getMessage().contains("RuntimeException"),
                "No debe contener 'RuntimeException' en el mensaje");

        // Verificar que se llamaron las dependencias correctas
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmail("test@mail.com");
        verifyNoInteractions(jwtService);
    }

    // ==================== TEST 8: Login con email ====================

    /**
     * Test 8: login() con email válido devuelve LoginResponse con tokens y datos del usuario.
     *
     * WHAT: Verifica que login() autentica por email y devuelve una respuesta
     * que incluye email, username, role, y tokens.
     * WHY: El frontend envía email+password. Spring Security autentica con
     * UsernamePasswordAuthenticationToken(email, password). El servicio debe
     * buscar al usuario por email después de autenticar.
     */
    @Test
    void login_withEmail_ReturnsLoginResponse() {
        // Given: credenciales válidas por email
        LoginRequest request = new LoginRequest("test@mail.com", "password123");

        // authenticationManager.authenticate() no lanza excepción = credenciales válidas
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("test@mail.com", "password123"));

        User user = new User("test", "test@mail.com", "encoded-password", Role.SALES_CLERK);
        user.setId(1L);
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(jwtService.generateToken(eq(user), anyLong())).thenReturn("refresh-token");

        // When
        LoginResponse response = authService.login(request);

        // Then: respuesta completa con email + username
        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("SALES_CLERK", response.role());
        assertEquals("test@mail.com", response.email(),
                "El email en la respuesta debe coincidir con el del request");
        assertEquals("test", response.username(),
                "El username debe ser el display name del usuario");
        assertNotNull(response.createdAt());
        // WHAT: login response debe incluir la lista de roles
        assertNotNull(response.roles(), "roles no debe ser null");
        assertTrue(response.roles().contains("SALES_CLERK"),
                "roles debe contener SALES_CLERK: " + response.roles());

        // Verificar que el flujo de autenticación usó email
        verify(authenticationManager).authenticate(
                argThat(auth -> auth instanceof UsernamePasswordAuthenticationToken
                        && "test@mail.com".equals(auth.getPrincipal())
                        && "password123".equals(auth.getCredentials()))
        );
        verify(userRepository).findByEmail("test@mail.com");
        verify(jwtService).generateToken(user);
    }
}
