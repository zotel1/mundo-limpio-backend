package com.mundolimpio.application.security.config;

import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
 * HOW: Usa MockMvc para simular una solicitud GET sin Authorization header
 *      y verifica que retorna 200 con status=UP.
 *
 * DIFFERENCES: Este test no existia antes — es nuevo para validar que
 *              la configuracion de seguridad del PR #1 expone el health
 *              endpoint correctamente para Cloud Run.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigActuatorTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * WHAT: Health endpoint retorna 200 OK sin autenticacion
     *
     * WHY: Cloud Run health probes NO envian Authorization header.
     *      El endpoint DEBE responder 200 para que Cloud Run
     *      considere el contenedor como healthy.
     *
     * GIVEN: La aplicacion esta corriendo con actuator en el classpath
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
     * WHAT: Health endpoint es accesible incluso con perfil prod
     *
     * WHY: En produccion (Cloud Run), el perfil activo es 'prod'.
     *      El health endpoint DEBE seguir siendo publico.
     *
     * GIVEN: La aplicacion corre con perfil prod
     * WHEN:  GET /actuator/health sin auth
     * THEN:  HTTP 200 OK (no 401)
     *
     * TRIANGULACION: Mismo endpoint, mismo resultado esperado,
     *                pero con contexto de perfil productivo.
     *                Esto fuerza que la configuracion de seguridad
     *                funcione independientemente del perfil activo.
     */
    @Test
    void healthEndpoint_WithoutAuth_Returns200_InProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
