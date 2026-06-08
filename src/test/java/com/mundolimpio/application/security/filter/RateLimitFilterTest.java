package com.mundolimpio.application.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mundolimpio.application.common.dto.ErrorResponse;
import com.mundolimpio.application.security.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test para RateLimitFilter.
 *
 * POR QUÉ Mockito y no Spring Boot Test:
 * - RateLimitFilter es un Filter POJO con dependencias inyectadas por constructor.
 * - Podemos instanciarlo directamente con mocks para HttpServletRequest,
 *   HttpServletResponse, FilterChain y RateLimitConfig.
 * - No necesitamos levantar Tomcat ni Spring para verificar status, headers y body.
 *
 * QUÉ TESTEAMOS:
 * - Rate limit excedido → 429 + ErrorResponse + Retry-After header
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter filter;

    private ObjectMapper objectMapper;

    @Mock
    private RateLimitConfig rateLimitConfig;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Bucket bucket;

    @Mock
    private ConsumptionProbe probe;

    @Captor
    private ArgumentCaptor<String> headerValueCaptor;

    private StringWriter responseBodyWriter;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // registra JavaTimeModule para LocalDateTime

        filter = new RateLimitFilter(rateLimitConfig, objectMapper);

        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
    }

    @Test
    void shouldReturn429WithErrorResponseWhenRateLimitExceeded() throws Exception {
        // Given: rate limit excedido (no quedan tokens)
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        responseBodyWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBodyWriter));
        when(rateLimitConfig.resolveAuthBucket("192.168.1.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(5_000_000_000L); // 5 segundos

        // When: el filtro procesa el request
        filter.doFilter(request, response, filterChain);

        // Then: status 429
        verify(response).setStatus(429);

        // Then: Content-Type application/json
        verify(response).setHeader("Content-Type", "application/json");

        // Then: Retry-After header presente con el valor correcto (5 segundos)
        verify(response).setHeader(eq("Retry-After"), headerValueCaptor.capture());
        assertEquals("5", headerValueCaptor.getValue(), "Retry-After debe ser 5 segundos");

        // Then: body es un ErrorResponse válido
        verify(response).getWriter();
        String jsonBody = responseBodyWriter.toString().trim();
        assertNotNull(jsonBody);
        assertFalse(jsonBody.isEmpty(), "El body no debe estar vacío");

        ErrorResponse errorResponse = objectMapper.readValue(jsonBody, ErrorResponse.class);
        assertEquals("RATE_LIMIT_EXCEEDED", errorResponse.code());
        assertTrue(errorResponse.message().contains("5"),
                "El mensaje debe contener los segundos de espera: " + errorResponse.message());
        assertNotNull(errorResponse.timestamp(), "El timestamp no debe ser nulo");
        assertEquals("/api/v1/auth/login", errorResponse.path());

        // Then: el filtro NO debe llamar a filterChain.doFilter (request bloqueado)
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowRequestWhenRateLimitNotExceeded() throws Exception {
        // Given: hay tokens disponibles
        when(request.getRequestURI()).thenReturn("/api/v1/products");
        when(rateLimitConfig.resolveDefaultBucket("192.168.1.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(99L);

        // When: el filtro procesa el request
        filter.doFilter(request, response, filterChain);

        // Then: el filtro agrega header de remaining y pasa la cadena
        verify(response).setHeader("X-Rate-Limit-Remaining", "99");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldUseAuthBucketForAuthPaths() throws Exception {
        // Given: path de autenticación
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(rateLimitConfig.resolveAuthBucket("192.168.1.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(9L);

        // When: request a /api/v1/auth/login
        filter.doFilter(request, response, filterChain);

        // Then: se usó el bucket de auth
        verify(rateLimitConfig).resolveAuthBucket("192.168.1.1");
        verify(rateLimitConfig, never()).resolveDefaultBucket(anyString());
    }

    @Test
    void shouldUseDefaultBucketForNonAuthPaths() throws Exception {
        // Given: path NO autenticación
        when(request.getRequestURI()).thenReturn("/api/v1/products");
        when(rateLimitConfig.resolveDefaultBucket("192.168.1.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(99L);

        // When: request a /api/v1/products
        filter.doFilter(request, response, filterChain);

        // Then: se usó el bucket default
        verify(rateLimitConfig).resolveDefaultBucket("192.168.1.1");
        verify(rateLimitConfig, never()).resolveAuthBucket(anyString());
    }
}
