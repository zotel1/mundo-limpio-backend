package com.mundolimpio.application.common.handler;

import com.mundolimpio.application.common.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;

import java.util.Set;

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

    // ==================== TEST 3: HttpRequestMethodNotSupportedException → 405 ====================

    /**
     * Test 3: handleHttpRequestMethodNotSupported retorna 405 con METHOD_NOT_ALLOWED.
     *
     * WHAT: Verifica que HttpRequestMethodNotSupportedException se traduce a
     * HTTP 405 con código "METHOD_NOT_ALLOWED".
     *
     * WHY: Sin este handler, el cliente recibe un 405 genérico de Spring o
     * un 500. El status correcto es 405 METHOD_NOT_ALLOWED.
     */
    @Test
    void shouldReturn405WhenHttpRequestMethodNotSupported() {
        // Given: método HTTP no soportado
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH");

        // When: el handler procesa la excepción
        ResponseEntity<ErrorResponse> response = handler.handleHttpRequestMethodNotSupported(ex, webRequest);

        // Then: 405 METHOD_NOT_ALLOWED con código METHOD_NOT_ALLOWED
        assertNotNull(response);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().code());
        assertTrue(response.getBody().message().contains("PATCH"),
                "El mensaje debe contener el método no soportado: " + response.getBody().message());
        assertNotNull(response.getBody().timestamp());
        assertEquals("/api/v1/auth/login", response.getBody().path());
    }

    /**
     * Test 3b: Triangulación — otro método HTTP no soportado.
     */
    @Test
    void shouldReturn405WhenAnotherMethodNotSupported() {
        // Given: DELETE no soportado
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<ErrorResponse> response = handler.handleHttpRequestMethodNotSupported(ex, webRequest);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().code());
        assertTrue(response.getBody().message().contains("DELETE"));
    }

    // ==================== TEST 4: HttpMediaTypeNotSupportedException → 415 ====================

    /**
     * Test 4: handleHttpMediaTypeNotSupported retorna 415 con UNSUPPORTED_MEDIA_TYPE.
     *
     * WHAT: Verifica que HttpMediaTypeNotSupportedException se traduce a
     * HTTP 415 con código "UNSUPPORTED_MEDIA_TYPE".
     *
     * WHY: Sin este handler, el cliente recibe un 415 genérico de Spring o
     * un 500. El status correcto es 415 UNSUPPORTED_MEDIA_TYPE.
     */
    @Test
    void shouldReturn415WhenHttpMediaTypeNotSupported() throws Exception {
        // Given: media type no soportado
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("text/plain");

        // When: el handler procesa la excepción
        ResponseEntity<ErrorResponse> response = handler.handleHttpMediaTypeNotSupported(ex, webRequest);

        // Then: 415 UNSUPPORTED_MEDIA_TYPE con código UNSUPPORTED_MEDIA_TYPE
        assertNotNull(response);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().code());
        assertNotNull(response.getBody().timestamp());
        assertEquals("/api/v1/auth/login", response.getBody().path());
    }

    // ==================== TEST 5: MissingServletRequestParameterException → 400 ====================

    /**
     * Test 5: handleMissingServletRequestParameter retorna 400 con MISSING_PARAMETER.
     *
     * WHAT: Verifica que MissingServletRequestParameterException se traduce a
     * HTTP 400 con código "MISSING_PARAMETER".
     *
     * WHY: Sin este handler, un parámetro requerido faltante caía al catch-all → 500.
     * El status correcto es 400 BAD_REQUEST.
     */
    @Test
    void shouldReturn400WhenMissingServletRequestParameter() {
        // Given: parámetro requerido faltante
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("email", "String");

        // When: el handler procesa la excepción
        ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestParameter(ex, webRequest);

        // Then: 400 BAD_REQUEST con código MISSING_PARAMETER
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MISSING_PARAMETER", response.getBody().code());
        assertEquals("Required parameter 'email' is missing", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
        assertEquals("/api/v1/auth/login", response.getBody().path());
    }

    /**
     * Test 5b: Triangulación — otro parámetro faltante.
     */
    @Test
    void shouldReturn400WhenAnotherParameterMissing() {
        // Given: otro parámetro faltante
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("password", "String");

        ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestParameter(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MISSING_PARAMETER", response.getBody().code());
        assertEquals("Required parameter 'password' is missing", response.getBody().message());
    }

    // ==================== TEST 6: ConstraintViolationException → 400 ====================

    /**
     * Test 6: handleConstraintViolation retorna 400 con VALIDATION_ERROR.
     *
     * WHAT: Verifica que ConstraintViolationException se traduce a
     * HTTP 400 con código "VALIDATION_ERROR" y las violaciones concatenadas.
     *
     * WHY: Sin este handler, violaciones de constraint en parámetros
     * caían al catch-all → 500. El status correcto es 400 BAD_REQUEST.
     */
    @Test
    void shouldReturn400WhenConstraintViolation() {
        // Given: violación de constraint
        ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
        when(violation1.getMessage()).thenReturn("El email no puede estar vacío");
        ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
        when(violation2.getMessage()).thenReturn("El formato de email es inválido");

        ConstraintViolationException ex = new ConstraintViolationException(
                "Validation failed", Set.of(violation1, violation2)
        );

        // When: el handler procesa la excepción
        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex, webRequest);

        // Then: 400 BAD_REQUEST con código VALIDATION_ERROR
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
        assertTrue(response.getBody().message().contains("El email no puede estar vacío"),
                "El mensaje debe contener la primera violación");
        assertTrue(response.getBody().message().contains("El formato de email es inválido"),
                "El mensaje debe contener la segunda violación");
        assertNotNull(response.getBody().timestamp());
        assertEquals("/api/v1/auth/login", response.getBody().path());
    }
}
