package com.mundolimpio.application.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.http.HttpStatus;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de seguridad para el acceso publico al catalogo de productos.
 *
 * WHAT: Verifica que todos los endpoints de productos bajo /api/v1/products
 *       sean accesibles SIN autenticacion, despues del fix del typo en
 *       SecurityConfig (singular → plural).
 *
 * WHY: SecurityConfig.java linea 75 tenia /api/v1/product/** (singular)
 *      pero ProductController mapea a /api/v1/products (plural).
 *      Spring Security AntPathMatcher nunca hacia match, por lo que todos
 *      los endpoints de productos requerian autenticacion incorrectamente.
 *      Este test prueba que el fix funciona y previene regresiones.
 *
 * HOW: SpringBootTest con RANDOM_PORT levanta Tomcat real con seguridad completa.
 *      MockMvc envia requests sin Authorization header y verifica que los
 *      endpoints publicos responden 200 (no 401). Contiene su propio
 *      PostgreSQLContainer para evitar conflictos con AbstractIntegrationTest.
 *
 * DIFFERENCES: Contiene su propio @Container para PostgreSQL (mismo patron
 *              que SecurityConfigActuatorTest). No extiende AbstractIntegrationTest
 *              para mantener el test independiente de mock beans externos.
 */
@org.springframework.boot.test.context.SpringBootTest(
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityConfigProductAccessTest {

    /**
     * Container PostgreSQL dedicado para este test.
     * WHAT: Instancia de PostgreSQL 16 para tests de acceso a productos
     * WHY: Contenedor propio evita conflictos de ciclo de vida con
     *      otras test classes que usan AbstractIntegrationTest.
     *      postgres:16-alpine es la misma imagen que usa docker-compose.
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
     * WHAT: GET /api/v1/products sin autenticacion retorna 200 OK.
     *
     * WHY: El catalogo de productos debe ser PUBLICO — los clientes Flutter
     *      y el sitio web pueden ver productos sin necesidad de login.
     *      SecurityConfig debe tener .permitAll() con el path CORRECTO
     *      (plural: /api/v1/products/**).
     *
     * GIVEN: La aplicacion corre con SecurityConfig cargado,
     *        sin header Authorization
     * WHEN:  GET /api/v1/products
     * THEN:  HTTP 200 OK (lista de productos, puede ser vacia)
     *
     * PROD-ACCESS-001 Scenario: List active products without auth
     */
    @Test
    void getProducts_WithoutAuth_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    /**
     * WHAT: GET /api/v1/products con usuario autenticado tambien retorna 200.
     *
     * WHY: Triangulacion — mismo endpoint, diferente estado de autenticacion.
     *      Verifica que permitAll() aplica a CUALQUIER estado de auth
     *      (no solo usuarios anonimos). Si con @WithMockUser retornara 403,
     *      indicaria que la regla de seguridad es incorrecta.
     *
     * GIVEN: Un usuario autenticado con rol generico
     * WHEN:  GET /api/v1/products
     * THEN:  HTTP 200 OK
     */
    @Test
    @org.springframework.security.test.context.support.WithMockUser
    void getProducts_WithAuth_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    /**
     * WHAT: GET /api/v1/products/1 sin autenticacion NO retorna 401.
     *
     * WHY: El endpoint de detalle de producto por ID tambien debe ser
     *      publico. La respuesta puede ser 200 (si el producto existe)
     *      o 404 (si no existe), pero NUNCA 401. Si retorna 401,
     *      significa que SecurityConfig esta bloqueando el acceso
     *      incorrectamente (el typo singular/plural).
     *
     * GIVEN: La aplicacion corre sin productos precargados,
     *        sin header Authorization
     * WHEN:  GET /api/v1/products/1
     * THEN:  Status NO es 401 Unauthorized (puede ser 404 por falta de datos)
     *
     * PROD-ACCESS-001 Scenario: Get product by ID without auth
     */
    @Test
    void getProductById_WithoutAuth_Not401() throws Exception {
        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
    }

    /**
     * WHAT: OPTIONS /api/v1/products sin autenticacion retorna 200.
     *
     * WHY: Los preflight CORS (OPTIONS) deben ser manejados ANTES que el
     *      filtro de autenticacion. SecurityConfig tiene:
     *      .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
     *      Este test verifica que los preflight OPTIONS a endpoints de
     *      productos funcionan correctamente (el CorsFilter se ejecuta
     *      antes que Spring Security).
     *
     * GIVEN: La aplicacion corre con CorsConfig y SecurityConfig cargados
     * WHEN:  OPTIONS /api/v1/products
     * THEN:  HTTP 200 OK (CORS preflight exitoso)
     *
     * Edge Case: Preflight OPTIONS to product endpoints
     */
    @Test
    void optionsProducts_WithoutAuth_Returns200() throws Exception {
        mockMvc.perform(options("/api/v1/products"))
                .andExpect(status().isOk());
    }

    /**
     * WHAT: GET /api/v1/sales sin autenticacion retorna 401.
     *
     * WHY: Regression guard — verifica que el fix del path de productos
     *      NO rompe la proteccion de otros endpoints. /api/v1/sales NO
     *      tiene .permitAll() en SecurityConfig, por lo que debe caer en
     *      .anyRequest().authenticated() y retornar 401 sin token JWT.
     *      Si este test fallara, indicaria que el fix aflojo la seguridad
     *      de endpoints que SI deben estar protegidos.
     *
     * GIVEN: La aplicacion corre con SecurityConfig cargado,
     *        sin header Authorization
     * WHEN:  GET /api/v1/sales
     * THEN:  HTTP 401 Unauthorized
     *
     * PROD-ACCESS-003: Confirma que .anyRequest().authenticated()
     * sigue aplicando a endpoints no listados en permitAll()
     */
    @Test
    void getSales_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/sales"))
                .andExpect(status().isUnauthorized());
    }
}
