package com.mundolimpio.application.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuracion de CORS (Cross-Origin Resource Sharing) para la API.
 *
 * POR QUE esta clase:
 * - La app Flutter mobile corre en Android emulator (10.0.2.2) e iOS simulator (localhost),
 *   que son origenes diferentes al backend (localhost:8080).
 * - Sin CORS, el browser bloquea las requests cross-origin por politica de same-origin.
 *
 * POR QUE CorsConfigurationSource en vez de WebMvcConfigurer:
 * - CorsConfigurationSource (via @Bean) corre a nivel de FILTRO de Spring Security,
 *   lo que permite manejar preflight OPTIONS ANTES de la autenticacion.
 * - WebMvcConfigurer corre a nivel de INTERCEPTOR de Spring MVC (DESPUES de auth),
 *   lo que no capturaria los preflight OPTIONS antes de llegar a seguridad.
 * - Con @Bean, Spring Security lo auto-detecta cuando se usa .cors(Customizer.withDefaults()).
 *
 * POR QUE @Value externaliza los origenes:
 * - Los origenes pueden cambiar segun el entorno (desarrollo, staging, produccion).
 * - application.yml define los valores por defecto; se pueden overridear con
 *   variables de entorno (APPLICATION_CORS_ALLOWED_ORIGINS).
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    /**
     * Constructor que recibe los origenes permitidos desde application.yml.
     *
     * POR QUE List<String>:
     * - Spring Boot parsea automaticamente los valores separados por comas
     *   como elementos de una lista.
     * - Ejemplo: "http://localhost:8080, http://10.0.2.2:8080" → ["http://localhost:8080", "http://10.0.2.2:8080"]
     *
     * @param allowedOrigins lista de origenes permitidos para CORS
     */
    public CorsConfig(@Value("${application.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Crea el bean CorsConfigurationSource que Spring Security usara para CORS.
     *
     * POR QUE UrlBasedCorsConfigurationSource:
     * - Permite registrar configuraciones CORS por patron de URL.
     * - Registramos /** para aplicar la misma configuracion a TODOS los endpoints.
     *
     * POR QUE allowCredentials(false):
     * - La autenticacion usa JWT Bearer token en Header, no cookies.
     * - allowCredentials=true requiere origenes especificos (no wildcards).
     * - Con false, podemos usar multi-value allowedOrigins sin restricciones.
     *
     * POR QUE maxAge(3600):
     * - El browser cachea la respuesta del preflight OPTIONS por 1 hora.
     * - Reduce latencia en requests consecutivos de la app Flutter.
     * - 3600 segundos es un balance entre seguridad y performance.
     *
     * POR QUE los headers expuestos incluyen Authorization:
     * - El cliente Flutter necesita leer el header Authorization de la respuesta
     *   (para JWT). Sin exposedHeaders, el browser oculta los headers al JS.
     *
     * @return CorsConfigurationSource configurado para toda la API
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
