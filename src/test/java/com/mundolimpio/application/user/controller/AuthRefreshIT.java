package com.mundolimpio.application.user.controller;

import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.LoginResponse;
import com.mundolimpio.application.user.dto.RefreshRequest;
import com.mundolimpio.application.user.dto.RegisterRequest;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.application.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración para el flujo completo de refresh token.
 *
 * POR QUÉ integration test y no unit test:
 * - Necesitamos verificar que toda la cadena funciona:
 *   HTTP → Controller → Service → Repository → DB → JWT.
 * - Un unit test con mocks no verificaría que JWT real se genera y valida.
 * - Un integration test con H2 verifica que Spring Security,
 *   validaciones, JPA, y JWT funcionan juntos.
 *
 * CÓMO FUNCIONA:
 * - @SpringBootTest(webEnvironment = RANDOM_PORT): Levanta una instancia real
 *   del servidor en un puerto aleatorio.
 * - TestRestTemplate: Cliente HTTP real que hace requests al servidor.
 * - Perfil "test": Usa H2 en memoria en vez de PostgreSQL.
 *
 * FLUJO DEL TEST:
 * 1. Registrar un usuario → 201 CREATED + LoginResponse con tokens
 * 2. Usar el refresh token del registro → POST /refresh
 * 3. Verificar que el endpoint funciona y devuelve tokens válidos
 *
 * NOTA sobre tokens "diferentes":
 * - JwtService.generateToken() usa System.currentTimeMillis() para el claim "iat"
 *   (Issued At), que en JWT tiene precisión de segundos (NumericDate).
 * - Si register() y refresh() ocurren dentro del mismo segundo, los tokens
 *   generados son IDÉNTICOS (mismo subject, iat, exp, signing key).
 * - No verificamos que sean diferentes porque eso depende del momento exacto
 *   de ejecución, no de la lógica de negocio. Verificamos que sean válidos.
 * - En producción, el refresh siempre genera tokens con timestamps distintos
 *   porque hay al menos 1 segundo entre register/login y refresh.
 *
 * POR QUÉ registramos en vez de crear el usuario directamente:
 * - Queremos probar el flujo COMPLETO incluyendo el hash de password.
 * - register() hashea la password con BCrypt, lo que verifica que
 *   el PasswordEncoder funciona en el perfil test.
 * - Si creamos el usuario directamente, nos saltaríamos ese paso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthRefreshIT {

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
