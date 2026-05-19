package com.mundolimpio.application.security.config;

import com.mundolimpio.config.AbstractIntegrationTest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de seguridad para el endpoint /actuator/health de Cloud Run.
 *
 * WHAT: Verifica que el health endpoint de Actuator sea accesible SIN
 *       autenticacion, como requieren los health probes de Cloud Run.
 *
 * WHY: Cloud Run envia solicitudes HTTP a /actuator/health SIN headers
 *      de autenticacion. Si Spring Security bloquea este endpoint,
 *      Cloud Run asume que el contenedor esta muerto y lo reinicia.
 *      Esto causaria un loop infinito de reinicios.
 *
 * HOW: Usa su propio PostgreSQLContainer (no hereda de AbstractIntegrationTest
 *      para evitar conflicto de containers entre test classes).
 *      SpringBootTest con RANDOM_PORT levanta Tomcat real con seguridad completa.
 *      MockMvc verifica que el endpoint responde 200 sin auth header.
 *
 * DIFFERENCES: Contiene su propio @Container para PostgreSQL en vez de heredar
 *              el de AbstractIntegrationTest. Esto asegura que el container
 *              se inicializa correctamente en el contexto de este test.
 */
@org.springframework.boot.test.context.SpringBootTest(
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityConfigActuatorTest {

    /**
     * Container PostgreSQL dedicado para este test.
     * WHAT: Instancia de PostgreSQL 16 para tests del health endpoint
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
     * WHAT: Health endpoint retorna 200 OK sin autenticacion.
     *
     * WHY: Cloud Run health probes NO envian Authorization header.
     *      El endpoint DEBE responder 200 para que Cloud Run
     *      considere el contenedor como healthy.
     *
     * GIVEN: La aplicacion corre con actuator en el classpath
     *        y SecurityConfig cargado
     * WHEN:  GET /actuator/health sin header Authorization
     * THEN:  HTTP 200 OK con body { "status": "UP" }
     */
    @Test
    void healthEndpoint_WithoutAuth_Returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * WHAT: Health endpoint sigue siendo accesible incluso con
     *       usuario autenticado (triangulacion).
     *
     * WHY: El endpoint debe ser publico para TODOS, autenticados o no.
     *      Verificar que un @WithMockUser tambien obtiene 200 (no 403)
     *      confirma que requestMatchers funciona para cualquier estado
     *      de autenticacion.
     *
     * GIVEN: Un usuario autenticado con rol generico
     * WHEN:  GET /actuator/health con auth
     * THEN:  HTTP 200 OK (no 403 Forbidden)
     *
     * TRIANGULACION: Mismo endpoint, diferente estado de auth.
     */
    @Test
    @org.springframework.security.test.context.support.WithMockUser
    void healthEndpoint_WithAuth_Returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
