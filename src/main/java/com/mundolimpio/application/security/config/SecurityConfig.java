package com.mundolimpio.application.security.config;

import com.mundolimpio.application.security.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Configuración principal de seguridad de la aplicación.
 *
 * POR QUÉ ESTA IMPLEMENTACIÓN:
 * - Stateless (sin sesiones): cada request se autentica con JWT, no usamos sesiones HTTP.
 *   Esto es esencial para APIs REST que pueden ser consumidas por múltiples clientes.
 * - CSRF disabled: CSRF solo aplica a browsers con cookies. Como usamos JWT en headers,
 *   no necesitamos protección CSRF (los browsers no envían JWT automáticamente).
 * - methodSecurity enabled: permite usar @PreAuthorize en controllers para control granular.
 * - HttpStatusEntryPoint(UNAUTHORIZED): cuando un request no autenticado intenta acceder
 *   a un endpoint protegido, retorna 401 en vez de 403 (comportamiento REST estándar).
 * - CORS habilitado: permite que la app Flutter mobile acceda a la API desde orígenes
 *   cruzados (Android emulator 10.0.2.2, iOS simulator localhost). CorsConfig.java
 *   define el bean CorsConfigurationSource que se inyecta explicitamente en el
 *   configurador CORS via .cors(cors -> cors.configurationSource(...)).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                // CORS debe ir ANTES que cualquier configuracion de seguridad.
                // POR QUE: Los preflight OPTIONS de CORS deben ser manejados antes de
                // llegar al filter de autenticacion. Si CORS va despues de auth,
                // los preflight OPTIONS a endpoints protegidos retornarian 401.
                // COMO: Inyectamos explicitamente CorsConfigurationSource (definido en
                // CorsConfig.java) en el configurador CORS. Esto garantiza que Spring
                // Security use NUESTRA configuracion, no una auto-detectada.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Retorna 401 cuando no hay autenticación (en vez de 403 por defecto)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        // Permitir OPTIONS sin autenticacion para CORS preflight.
                        // POR QUE: Los preflight CORS son OPTIONS sin headers de auth.
                        // El CorsFilter (configurado en .cors() arriba) valida el origen.
                        // Si Spring Security bloquea el OPTIONS, el browser nunca recibe
                        // los headers CORS y rechaza el request real.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/sku/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/{id}").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Health check de Cloud Run — debe ser publico
                        // WHAT: Permitir /actuator/health sin autenticacion
                        // WHY: Cloud Run envia health probes HTTP sin Authorization header.
                        //      Si bloqueamos este endpoint, el contenedor nunca pasa
                        //      el health check y Cloud Run lo reinicia en loop infinito.
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .xssProtection(xss -> xss
                                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder  passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
