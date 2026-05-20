package com.mundolimpio.application.security.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integracion para el manejo de JWT expirados en JwtAuthenticationFilter.
 *
 * WHAT: Verifica que JwtAuthenticationFilter maneja correctamente tokens JWT
 *       expirados, validos y ausentes en endpoints publicos y protegidos.
 *
 * WHY: El codigo actual de JwtAuthenticationFilter llama a
 *      jwtService.extractUsername(jwt) FUERA del try-catch (linea 51).
 *      Cuando el token esta expirado, parseSignedClaims() lanza
 *      ExpiredJwtException que se propaga sin ser capturada, causando un
 *      error incluso en endpoints publicos (ej: /api/v1/products).
 *      Este bug impide que los clientes vean el catalogo de productos si
 *      envian un token expirado en el request.
 *
 * HOW: SpringBootTest con RANDOM_PORT levanta Tomcat real con toda la
 *      cadena de seguridad. MockMvc envia requests con tokens Bearer
 *      (expirados, validos, o ausentes) a traves del filtro completo.
 *      Contiene su propio PostgreSQLContainer (mismo patron que
 *      SecurityConfigProductAccessTest).
 *
 * DIFFERENCES: Usa Jwts.builder() para generar tokens manualmente con
 *              control preciso de fechas de expiracion (no inyecta
 *              JwtService). La secret key es la misma que el default
 *              en application.yml (HS256, 256-bit hex).
 */
@org.springframework.boot.test.context.SpringBootTest(
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class JwtAuthenticationFilterExpiredTokenTest {

    /**
     * Container PostgreSQL dedicado para este test.
     * WHAT: Instancia de PostgreSQL 16 para tests de filtro JWT.
     * WHY: Contenedor propio evita conflictos de ciclo de vida con
     *      otras test classes (ej: AbstractIntegrationTest).
     *      postgres:16-alpine es la misma imagen que docker-compose.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    /**
     * Secret key HMAC-SHA256 — mismo valor que application.security.jwt.secret-key
     * en application.yml (JWT_SECRET_KEY no seteado como env var en test).
     * WHAT: 256-bit key en hex usado para firmar y verificar tokens.
     * WHY: Debe coincidir EXACTAMENTE con la key que usa JwtService para
     *      que los tokens generados en el test sean validos para la app.
     */
    private static final byte[] KEY_BYTES = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970".getBytes();

    private static String expiredToken;
    private static String validToken;

    /**
     * WHAT: Genera tokens JWT para los 5 escenarios de test.
     *
     * WHY: expiredToken tiene expiracion en el pasado (hace 12h) —
     *      reproduce el bug donde ExpiredJwtException se propaga sin
     *      ser capturada. validToken tiene expiracion en el futuro (1h) —
     *      sirve como regression guard para el flujo de token valido.
     *
     * HOW: Jwts.builder() con subject arbitrario ("test@test.com" /
     *      "valid@test.com"). El usuario no necesita existir en la DB
     *      porque /api/v1/products es publico (permitAll).
     */
    @BeforeAll
    static void setUpTokens() {
        SecretKey key = Keys.hmacShaKeyFor(KEY_BYTES);
        long now = System.currentTimeMillis();

        // Token expirado: emitido hace 1 dia, expiro hace 12 horas
        expiredToken = Jwts.builder()
                .subject("test@test.com")
                .issuedAt(new Date(now - 86_400_000))
                .expiration(new Date(now - 43_200_000))
                .signWith(key)
                .compact();

        // Token valido: emitido ahora, expira en 1 hora
        validToken = Jwts.builder()
                .subject("valid@test.com")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000))
                .signWith(key)
                .compact();
    }

    // ═══════════════════════════════════════════════════════════════════
    // JWT-FILTER-001: Expired token on permitAll endpoint → NOT 401
    // ═══════════════════════════════════════════════════════════════════

    /**
     * WHAT: GET /api/v1/products con token expirado NO retorna 401.
     *
     * WHY: /api/v1/products es .permitAll() en SecurityConfig.
     *      Un token expirado no deberia bloquear el acceso a
     *      endpoints publicos. Este es el escenario que FALLA
     *      con el codigo actual (bug principal).
     *
     * GIVEN: Token JWT expirado hace 12 horas
     * WHEN:  GET /api/v1/products con Authorization: Bearer <expirado>
     * THEN:  Status NO es 401 Unauthorized
     *
     * JWT-FILTER-001 Scenario: Expired token on GET /api/v1/products
     */
    @Test
    void publicEndpoint_WithExpiredToken_ShouldNotReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
    }

    /**
     * WHAT: GET /api/v1/products con token expirado NO retorna 403.
     *
     * WHY: Triangulacion — verifica que ademas de no ser 401,
     *      tampoco es 403 Forbidden. Si retornara 403, indicaria
     *      que Spring Security interpreta el token expirado como
     *      autenticacion fallida en vez de ignorarlo.
     *
     * GIVEN: Token JWT expirado hace 12 horas
     * WHEN:  GET /api/v1/products con Authorization: Bearer <expirado>
     * THEN:  Status NO es 403 Forbidden
     */
    @Test
    void publicEndpoint_WithExpiredToken_ShouldNotReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().is(not(HttpStatus.FORBIDDEN.value())));
    }

    // ═══════════════════════════════════════════════════════════════════
    // JWT-FILTER-002: Expired token on protected endpoint → 401
    // ═══════════════════════════════════════════════════════════════════

    /**
     * WHAT: GET /api/v1/sales con token expirado retorna 401.
     *
     * WHY: /api/v1/sales NO tiene .permitAll() — cae en
     *      .anyRequest().authenticated(). Con token expirado,
     *      el filtro no setea SecurityContext, por lo que
     *      Spring Security debe retornar 401 via HttpStatusEntryPoint.
     *      Este test es regression guard: verifica que el fix
     *      NO rompe la proteccion de endpoints restringidos.
     *
     * GIVEN: Token JWT expirado hace 12 horas
     * WHEN:  GET /api/v1/sales con Authorization: Bearer <expirado>
     * THEN:  HTTP 401 Unauthorized
     *
     * JWT-FILTER-002 Scenario: Expired token on protected endpoint
     */
    @Test
    void protectedEndpoint_WithExpiredToken_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/sales")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════════
    // JWT-FILTER-003 regression: Valid token still works
    // ═══════════════════════════════════════════════════════════════════

    /**
     * WHAT: GET /api/v1/products con token valido retorna 200.
     *
     * WHY: Regression guard — verifica que el fix no rompe el
     *      flujo normal de tokens validos en endpoints publicos.
     *      El token es estructuralmente valido (no expirado,
     *      firmado correctamente). El usuario puede no existir
     *      en la DB, pero /api/v1/products es permitAll de
     *      todas formas.
     *
     * GIVEN: Token JWT valido (expira en 1 hora)
     * WHEN:  GET /api/v1/products con Authorization: Bearer <valido>
     * THEN:  HTTP 200 OK
     */
    @Test
    void publicEndpoint_WithValidToken_ShouldWork() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
    }

    // ═══════════════════════════════════════════════════════════════════
    // JWT-FILTER-004 regression: No token still works
    // ═══════════════════════════════════════════════════════════════════

    /**
     * WHAT: GET /api/v1/products sin token retorna 200.
     *
     * WHY: Regression guard — verifica que el fix no rompe el
     *      acceso sin token a endpoints publicos. Sin header
     *      Authorization, el filtro hace early return en la
     *      validacion inicial (authHeader == null).
     *
     * GIVEN: Request sin header Authorization
     * WHEN:  GET /api/v1/products
     * THEN:  HTTP 200 OK
     *
     * JWT-FILTER-004 Scenario: No Authorization header on GET /api/v1/products
     */
    @Test
    void publicEndpoint_WithoutToken_ShouldWork() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }
}
