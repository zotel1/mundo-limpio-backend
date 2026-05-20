package com.mundolimpio.application.user.exception;

import com.mundolimpio.application.user.dto.RegisterRequest;
import com.mundolimpio.application.user.service.AuthService;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests para AuthExceptionHandler.
 * <p>
 * WHAT: Verifica que {@code handleDuplicateEmail} captura
 * ResponseStatusException(409) y retorna 409 EMAIL_ALREADY_IN_USE,
 * y que NO intercepta ResponseStatusException con otros status codes.
 * <p>
 * WHY: AuthExceptionHandler filtra por status CONFLICT para email duplicado.
 * Si el status no es 409, re-lanza la excepción para que otro handler la procese.
 * Estos tests verifican ese filtro.
 * <p>
 * HOW: Mockeamos AuthService.register() para lanzar ResponseStatusException
 * con distintos status codes y verificamos la respuesta HTTP via MockMvc.
 * La ruta /api/v1/auth/register es permitAll() en SecurityConfig.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthExceptionHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    /**
     * Test 1: ResponseStatusException(409 CONFLICT) → 409 EMAIL_ALREADY_IN_USE.
     * <p>
     * ESCENARIO: GIVEN el email ya existe → WHEN se registra con email duplicado
     * → THEN 409 CONFLICT con código "EMAIL_ALREADY_IN_USE".
     */
    @Test
    void handleDuplicateEmail_WhenConflict_ShouldReturn409WithCode() throws Exception {
        // Given: AuthService.register() lanza ResponseStatusException(409)
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT, "Email already in use"));

        // When: POST /api/v1/auth/register con email duplicado
        // Then: 409 CONFLICT con código EMAIL_ALREADY_IN_USE
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@mail.com\",\"password\":\"Pass1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("Email already in use"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    /**
     * Test 2 (TRIANGULATE): ResponseStatusException(400) NO es interceptado.
     * <p>
     * ESCENARIO: GIVEN un error con status != 409 → WHEN AuthExceptionHandler
     * evalúa la excepción → THEN re-lanza (no produce EMAIL_ALREADY_IN_USE).
     * Spring maneja la excepción re-lanzada según su status code (400).
     * <p>
     * NOTA: Cuando handleDuplicateEmail re-lanza la excepción, Spring usa
     * ResponseStatusExceptionResolver que devuelve 400 pero sin cuerpo JSON
     * estructurado. Por eso solo verificamos que el status NO es 409
     * (no fue interceptado por handleDuplicateEmail).
     */
    @Test
    void handleDuplicateEmail_WhenNotConflict_ShouldNotHandle() throws Exception {
        // Given: AuthService.register() lanza ResponseStatusException(400)
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Validation failed"));

        // When: POST /api/v1/auth/register
        // Then: 400 Bad Request (manejado por Spring default, NO por AuthExceptionHandler)
        // NO debe retornar 409 — el handler solo intercepta CONFLICT
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(status().is(not(HttpStatus.CONFLICT.value())));
    }
}
