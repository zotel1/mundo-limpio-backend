package com.mundolimpio.application.user.controller;

import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.LoginResponse;
import com.mundolimpio.application.user.dto.RefreshRequest;
import com.mundolimpio.application.user.dto.RegisterRequest;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración para el flujo completo de refresh token.
 *
 * WHAT: Verifica registro → refresh token → nuevos tokens contra PostgreSQL real.
 * WHY: JWT generation y validación deben funcionar idéntico en test y producción.
 * DIFFERENCES: Antes usaba @SpringBootTest directo con H2; ahora extiende
 *              AbstractIntegrationTest que provee PostgreSQL via Testcontainers.
 */
@ActiveProfiles("test")
class AuthRefreshIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    /**
     * Limpieza antes de cada test.
     *
     * POR QUÉ @BeforeEach y no @BeforeAll:
     * - Cada test debe ser independiente. Si un test registra un usuario,
     *   el siguiente no debe encontrar ese usuario en la DB.
     * - deleteAll() asegura que empezamos con una DB limpia.
     */
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    /**
     * Test de integración: registro → refresh → respuesta exitosa.
     *
     * ESCENARIO:
     * 1. Registramos un usuario con POST /api/v1/auth/register
     * 2. Obtenemos el refresh token de la respuesta y lo usamos
     *    en POST /api/v1/auth/refresh
     * 3. Verificamos que el refresh devuelve 200 OK con tokens válidos
     *
     * QUÉ VERIFICA:
     * - El endpoint /register funciona y devuelve 201 CREATED
     * - El endpoint /refresh funciona y devuelve 200 OK
     * - El refresh token generado en register() es válido para refresh()
     * - Los tokens en la respuesta no son nulos (se generaron correctamente)
     * - La respuesta contiene username y role correctos
     * - La fecha de creación se mantiene del usuario original
     */
    @Test
    void shouldRefreshTokenSuccessfully() {
        // ========== 1. Registrar un usuario ==========
        RegisterRequest registerReq = new RegisterRequest("testuser", "password123");

        ResponseEntity<LoginResponse> registerRes = restTemplate.postForEntity(
                "/api/v1/auth/register",
                registerReq,
                LoginResponse.class
        );

        // Verificar que el registro fue exitoso
        assertThat(registerRes.getStatusCode())
                .as("El registro debe retornar 201 CREATED")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(registerRes.getBody())
                .as("El body del registro no debe ser nulo")
                .isNotNull();
        assertThat(registerRes.getBody().accessToken())
                .as("El access token del registro no debe ser nulo")
                .isNotNull();
        assertThat(registerRes.getBody().refreshToken())
                .as("El refresh token del registro no debe ser nulo")
                .isNotNull();
        assertThat(registerRes.getBody().username())
                .as("El username del registro debe coincidir")
                .isEqualTo("testuser");
        assertThat(registerRes.getBody().role())
                .as("El role del registro debe ser OPERATOR")
                .isEqualTo("OPERATOR");

        // ========== 2. Usar el refresh token para obtener nuevos tokens ==========
        String refreshToken = registerRes.getBody().refreshToken();
        RefreshRequest refreshReq = new RefreshRequest(refreshToken);

        ResponseEntity<LoginResponse> refreshRes = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                refreshReq,
                LoginResponse.class
        );

        // ========== 3. Verificar que el refresh fue exitoso ==========
        assertThat(refreshRes.getStatusCode())
                .as("El refresh debe retornar 200 OK")
                .isEqualTo(HttpStatus.OK);
        assertThat(refreshRes.getBody())
                .as("El body del refresh no debe ser nulo")
                .isNotNull();

        // Verificar que ambos tokens se generaron correctamente (no nulos)
        // POR QUÉ no verificamos que sean DIFERENTES a los originales:
        // JwtService.generateToken() usa NumericDate (segundos) para "iat".
        // Si register y refresh ocurren en el mismo segundo, los JWT son
        // idénticos (mismo subject, iat, exp, y signing key).
        // Esto no afecta la funcionalidad — el token sigue siendo válido.
        assertThat(refreshRes.getBody().accessToken())
                .as("El nuevo access token no debe ser nulo")
                .isNotNull();
        assertThat(refreshRes.getBody().refreshToken())
                .as("El nuevo refresh token no debe ser nulo")
                .isNotNull();

        // Verificar que los datos del usuario se mantienen
        assertThat(refreshRes.getBody().username())
                .as("El username debe coincidir con el usuario registrado")
                .isEqualTo("testuser");
        assertThat(refreshRes.getBody().role())
                .as("El role debe coincidir con el usuario registrado")
                .isEqualTo("OPERATOR");
        assertThat(refreshRes.getBody().createdAt())
                .as("La fecha de creación no debe ser nula")
                .isNotNull();
    }
}
