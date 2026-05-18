package com.mundolimpio.application.security.config;

import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para CorsConfig.
 *
 * POR QUÉ integration test y no unit test:
 * - Necesitamos verificar que el bean CorsConfigurationSource se crea correctamente
 *   con los valores de application.yml inyectados via @Value.
 * - Un unit test con mocks no verificaría la integración con Spring Boot.
 *
 * DIFFERENCES: Antes usaba @SpringBootTest directo con H2; ahora extiende
 *              AbstractIntegrationTest que provee PostgreSQL via Testcontainers.
 */
@ActiveProfiles("test")
class CorsConfigTest extends AbstractIntegrationTest {

    @Autowired(required = false)
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void corsConfigurationSource_ShouldHaveConfiguredOrigins() {
        // RED: Este test falla porque CorsConfig.java no existe todavia.
        // El bean CorsConfigurationSource es null → assertNotNull falla.
        assertNotNull(corsConfigurationSource,
                "El bean CorsConfigurationSource debe existir. " +
                "Crear CorsConfig.java con @Configuration y @Bean corsConfigurationSource()");

        // Simulamos un request para obtener la configuracion CORS
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        request.setMethod("OPTIONS");

        // Obtenemos la configuracion para la ruta /** (la registrada en CorsConfig)
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
        assertNotNull(config, "CorsConfiguration debe estar registrado para la ruta /**");

        // Verificar orígenes permitidos: http://localhost:8080 y http://10.0.2.2:8080
        // POR QUÉ estos orígenes: Flutter Android emulator (10.0.2.2) e iOS simulator (localhost)
        assertThat(config.getAllowedOrigins())
                .as("Orígenes permitidos por CORS")
                .contains("http://localhost:8080", "http://10.0.2.2:8080");

        // Verificar métodos HTTP permitidos
        assertThat(config.getAllowedMethods())
                .as("Métodos HTTP permitidos por CORS")
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

        // Verificar headers permitidos en requests CORS
        assertThat(config.getAllowedHeaders())
                .as("Headers permitidos en requests CORS")
                .contains("Authorization", "Content-Type", "Accept", "Origin");

        // Verificar headers expuestos al cliente (necesario para JWT)
        assertThat(config.getExposedHeaders())
                .as("Headers expuestos al cliente")
                .contains("Authorization");

        // Verificar que NO se requieren credenciales
        // POR QUÉ: Usamos JWT Bearer token, no cookies. allowCredentials=false
        // permite usar multi-value allowedOrigins sin restricciones de seguridad.
        assertFalse(config.getAllowCredentials(),
                "AllowCredentials debe ser false porque usamos JWT, no cookies");

        // Verificar cache de preflight: 1 hora
        // POR QUÉ: Reduce latencia en requests consecutivos del Flutter app
        assertEquals(3600L, config.getMaxAge().longValue(),
                "MaxAge debe ser 3600 segundos (1 hora)");
    }
}
