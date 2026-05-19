package com.mundolimpio.application.security.config;

import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integracion CORS para verificar que preflight OPTIONS funciona
 * antes de la autenticacion en la SecurityFilterChain.
 *
 * POR QUE MockMvc en vez de TestRestTemplate:
 * - MockMvc nos da acceso directo a los headers de respuesta sin las
 *   limitaciones de HttpURLConnection (que puede filtrar ciertos headers).
 * - MockMvc ejecuta el SecurityFilterChain completo, igual que TestRestTemplate.
 *
 * POR QUE este test:
 * - La app Flutter necesita enviar requests cross-origin desde el emulador.
 * - El browser envia un preflight OPTIONS antes del request real.
 * - Sin CORS configurado en SecurityFilterChain, el preflight retorna 401.
 * - Con .cors(Customizer.withDefaults()), Spring Security usa el bean
 *   CorsConfigurationSource para manejar el preflight y retorna 200.
 *
 * DIFFERENCES: Antes usaba @SpringBootTest directo con H2; ahora extiende
 *              AbstractIntegrationTest que provee PostgreSQL via Testcontainers.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightOPTIONS_WithAllowedOrigin_Returns200() throws Exception {
        // Given: Simulamos un preflight CORS como lo haria un browser.
        // Origin: El origen que hace la request cross-origin (app Flutter).
        // Access-Control-Request-Method: El metodo HTTP del request real.
        mockMvc.perform(request(HttpMethod.OPTIONS, "/api/v1/sales")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "GET"))
                // Then: Debe retornar 200 OK con headers CORS, SIN requerir autenticacion.
                // POR QUE el preflight OPTIONS es manejado por el CorsFilter de Spring Security
                // que corre ANTES del filter de autenticacion en la cadena.
                .andExpect(status().isOk())
                // Verificar que los headers CORS estan presentes en la respuesta
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
                // Verificar headers adicionales de CORS
                // NOTA: Access-Control-Allow-Headers solo se incluye cuando
                // el request trae Access-Control-Request-Headers.
                // En este test no lo enviamos, asi que no aparece en la respuesta.
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().exists("Access-Control-Expose-Headers"))
                .andExpect(header().exists("Access-Control-Max-Age"));
    }

    @Test
    void preflightOPTIONS_WithDisallowedOrigin_Returns403() throws Exception {
        // Given: Preflight desde un origen NO permitido
        mockMvc.perform(request(HttpMethod.OPTIONS, "/api/v1/sales")
                        .header("Origin", "https://evil-site.com")
                        .header("Access-Control-Request-Method", "GET"))
                // Then: No debe incluir Access-Control-Allow-Origin
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void actualGET_FromAllowedOrigin_StillRequiresAuth() throws Exception {
        // Given: Request GET desde origen permitido, SIN token JWT
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/sales")
                        .header("Origin", "http://localhost:8080"))
                // Then: Debe retornar 401 (no autenticado) PERO con CORS headers
                .andExpect(status().isUnauthorized())
                // Los headers CORS deben estar presentes incluso en 401
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }
}
