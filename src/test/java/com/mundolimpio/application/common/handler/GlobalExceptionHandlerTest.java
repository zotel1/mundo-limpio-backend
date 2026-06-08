package com.mundolimpio.application.common.handler;

import com.mundolimpio.application.common.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test para GlobalExceptionHandler.
 *
 * POR QUÉ Mockito y no Spring Boot Test:
 * - GlobalExceptionHandler es un POJO con métodos @ExceptionHandler.
 * - Podemos instanciarlo directamente y llamar a sus métodos con mocks.
 * - No necesitamos levantar Spring para verificar el status HTTP y el body.
 *
 * QUÉ TESTEAMOS:
 * - AuthenticationException handler → 401 + INVALID_CREDENTIALS
 * - HttpMessageNotReadableException handler → 400 + MALFORMED_JSON
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(webRequest.getDescription(false)).thenReturn("uri=/api/v1/auth/login");
    }

    // ==================== TEST 1: AuthenticationException → 401 ====================

    /**
     * Test 1: handleAuthenticationException retorna 401 con INVALID_CREDENTIALS.
     *
     * WHAT: Verifica que cualquier subtipo de AuthenticationException
     * (BadCredentialsException, DisabledException, LockedException, etc.)
     * se traduce a HTTP 401 con código "INVALID_CREDENTIALS".
     *
     * WHY: Sin este handler, AuthenticationException cae al catch-all → 500.
     * El status correcto para credenciales inválidas es 401 UNAUTHORIZED.
     */
    @Test
    void shouldReturn401WhenAuthenticationException() {
        // Given: cualquier excepción de autenticación
        AuthenticationException ex = new BadCredentialsException("Bad credentials");

        // When: el handler procesa la excepción
        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationException(ex, webRequest);

        // Then: 401 UNAUTHORIZED con código INVALID_CREDENTIALS
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_CREDENTIALS", response.getBody().code());
        assertEquals("Invalid email or password", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
        assertEquals("/api/v1/auth/login", response.getBody().path());
    }

    /**
     * Test 1b: Triangulación — DisabledException también da 401 INVALID_CREDENTIALS.
     *
     * WHY: El handler debe ser polimórfico y cubrir todos los subtipos
     * de AuthenticationException, no solo BadCredentialsException.
     */
    @Test
    void shouldReturn401WhenDisabledException() {
        // Given: un usuario deshabilitado
        AuthenticationException ex = new DisabledException("User account is disabled");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationException(ex, webRequest);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_CREDENTIALS", response.getBody().code());
    }

    /**
     * Test 1c: Triangulación — LockedException también da 401 INVALID_CREDENTIALS.
     */
    @Test
    void shouldReturn401WhenLockedException() {
        AuthenticationException ex = new LockedException("User account is locked");

        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationException(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_CREDENTIALS", response.getBody().code());
    }

    // ==================== TEST 2: HttpMessageNotReadableException → 400 ====================

    /**
     * Test 2: handleHttpMessageNotReadable retorna 400 con MALFORMED_JSON.
     *
     * WHAT: Verifica que HttpMessageNotReadableException se traduce a
     * HTTP 400 con código "MALFORMED_JSON" y el detalle de Jackson.
     *
     * WHY: Sin este handler, JSON malformado caía al catch-all → 500.
     * El status correcto es 400 BAD_REQUEST: el request del cliente
     * no puede ser parseado.
     */
    @Test
    void shouldReturn400WhenHttpMessageNotReadable() {
        // Given: un JSON malformado
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character '}'"
        );

        // When: el handler procesa la excepción
        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadable(ex, webRequest);

        // Then: 400 BAD_REQUEST con código MALFORMED_JSON
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MALFORMED_JSON", response.getBody().code());
        assertTrue(response.getBody().message().contains("JSON parse error"),
                "El mensaje debe contener el detalle de Jackson: " + response.getBody().message());
        assertNotNull(response.getBody().timestamp());
        assertEquals("/api/v1/auth/login", response.getBody().path());
    }

    /**
     * Test 2b: Triangulación — otro mensaje de error de Jackson.
     */
    @Test
    void shouldReturn400WhenJsonTruncated() {
        // Given: JSON truncado
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected end-of-input"
        );

        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadable(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MALFORMED_JSON", response.getBody().code());
        assertTrue(response.getBody().message().contains("Unexpected end-of-input"));
    }
}
