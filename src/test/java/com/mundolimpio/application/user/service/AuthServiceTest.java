package com.mundolimpio.application.user.service;

import com.mundolimpio.application.security.service.CustomUserDetailsService;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.LoginResponse;
import com.mundolimpio.application.user.dto.RefreshRequest;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

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
        testUser = new User("testuser", "encoded-password", Role.OPERATOR);
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
        assertEquals("OPERATOR", response.role(),
                "El rol debe coincidir con el del usuario");
        assertEquals("testuser", response.username(),
                "El username debe coincidir con el del usuario");
        assertNotNull(response.createdAt(),
                "La fecha de creación no debe ser nula");

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
}
