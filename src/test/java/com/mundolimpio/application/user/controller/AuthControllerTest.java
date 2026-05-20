package com.mundolimpio.application.user.controller;

import com.mundolimpio.application.user.dto.LoginResponse;
import com.mundolimpio.application.user.dto.RefreshRequest;
import com.mundolimpio.application.user.exception.InvalidRefreshTokenException;
import com.mundolimpio.application.user.service.AuthService;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit Test para AuthController.refresh() usando MockMvc.
 *
 * POR QUÉ @SpringBootTest + @AutoConfigureMockMvc y no @WebMvcTest:
 * - @WebMvcTest no carga SecurityConfig, JwtService, ni otros beans de seguridad.
 * - Pero SecurityFilterAutoConfiguration se activa y busca el bean JwtAuthenticationFilter,
 *   que a su vez necesita JwtService. Sin JwtService en el contexto, falla.
 * - @SpringBootTest carga el contexto completo, incluyendo SecurityConfig que define
 *   /api/v1/auth/** como permitAll(), y JwtService que necesita JwtAuthenticationFilter.
 * - @AutoConfigureMockMvc permite usar MockMvc para simular requests HTTP.
 * - @MockBean AuthService reemplaza el AuthService real para controlar el comportamiento.
 *
 * DIFFERENCES: Antes usaba @SpringBootTest directo con H2; ahora extiende
 *              AbstractIntegrationTest que provee PostgreSQL via Testcontainers.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    /**
     * Test 1: POST /refresh con token válido → 200 OK + LoginResponse.
     *
     * QUÉ VERIFICA:
     * - El controller acepta un RefreshRequest JSON válido.
     * - AuthService.refresh() es invocado con el request correcto.
     * - La respuesta HTTP es 200 OK.
     * - El body contiene el accessToken, refreshToken, role, y username.
     */
    @Test
    void shouldReturn200WhenRefreshSucceeds() throws Exception {
        // Given: el service devuelve un LoginResponse exitoso
        LoginResponse mockResponse = new LoginResponse(
                "new-access-token",
                "new-refresh-token",
                "OPERATOR",
                "testuser@mundolimpio.com",
                "testuser",
                Instant.now()
        );

        when(authService.refresh(any(RefreshRequest.class))).thenReturn(mockResponse);

        // When: hacemos POST a /api/v1/auth/refresh con un token válido
        // Then: debe retornar 200 OK con los datos del nuevo token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"valid-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.email").value("testuser@mundolimpio.com"))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    /**
     * Test 2: POST /refresh con token inválido → 401 UNAUTHORIZED.
     *
     * QUÉ VERIFICA:
     * - AuthService.refresh() lanza InvalidRefreshTokenException.
     * - AuthExceptionHandler captura la excepción y retorna 401.
     * - El body contiene el código de error "INVALID_REFRESH_TOKEN".
     *
     * POR QUÉ este test:
     * - El cliente necesita saber que su refresh token no es válido.
     * - Sin este handler, la excepción caería en el catch-all de GlobalExceptionHandler → 500.
     * - 401 es el status correcto: el cliente debe re-autenticarse.
     */
    @Test
    void shouldReturn401WhenRefreshTokenIsInvalid() throws Exception {
        // Given: el service lanza InvalidRefreshTokenException
        when(authService.refresh(any(RefreshRequest.class)))
                .thenThrow(new InvalidRefreshTokenException(
                        "El refresh token no es válido o ha expirado",
                        InvalidRefreshTokenException.RefreshError.INVALID
                ));

        // When: hacemos POST a /api/v1/auth/refresh con un token inválido
        // Then: debe retornar 401 UNAUTHORIZED con código de error
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"invalid-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value("El refresh token no es válido o ha expirado"));
    }
}
