package com.mundolimpio.application.user.controller;

import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.dto.LoginRequest;
import com.mundolimpio.application.user.dto.LoginResponse;
import com.mundolimpio.application.user.dto.RegisterRequest;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración para AuthController: registro y login.
 * <p>
 * WHAT: Verifica que POST /api/v1/auth/register y /login devuelven
 * CUSTOMER como rol por defecto en los campos role y roles.
 * WHY: Después del reseteo de BD, todos los usuarios nuevos deben
 * recibir CUSTOMER como rol base (solo lectura de productos).
 * DIFFERENCES: Antes register() creaba usuarios sin roles (role=null);
 * ahora asigna Role.CUSTOMER y nunca devuelve null en role.
 */
@ActiveProfiles("test")
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    /**
     * Limpieza antes de cada test.
     * WHY: Cada test debe ser independiente para evitar efectos secundarios.
     */
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    /**
     * POST /register → 201 + role=CUSTOMER + roles=[CUSTOMER].
     * <p>
     * WHAT: Verifica que un registro exitoso retorna CUSTOMER como rol
     * tanto en el campo deprecated role como en la lista roles.
     */
    @Test
    void register_shouldReturnCustomerRole() {
        RegisterRequest request = new RegisterRequest("newuser@test.com", "password123");

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/register",
                request,
                LoginResponse.class
        );

        assertThat(response.getStatusCode())
                .as("El registro exitoso debe retornar 201 CREATED")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .as("El body de la respuesta no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().role())
                .as("El campo role (deprecated) debe ser CUSTOMER")
                .isEqualTo("CUSTOMER");
        assertThat(response.getBody().roles())
                .as("La lista roles debe contener CUSTOMER")
                .contains("CUSTOMER");
        assertThat(response.getBody().roles())
                .as("El usuario debe tener exactamente 1 rol (CUSTOMER)")
                .hasSize(1);
        assertThat(response.getBody().accessToken())
                .as("El access token no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().refreshToken())
                .as("El refresh token no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().email())
                .as("El email debe coincidir con el registrado")
                .isEqualTo("newuser@test.com");
        assertThat(response.getBody().createdAt())
                .as("La fecha de creación no debe ser nula")
                .isNotNull();
    }

    /**
     * POST /login → 200 + role=CUSTOMER + roles=[CUSTOMER].
     * <p>
     * WHAT: Verifica que login retorna CUSTOMER para un usuario registrado.
     * Primero registra al usuario, luego hace login con las mismas credenciales.
     */
    @Test
    void login_shouldReturnCustomerRole() {
        // 1. Registrar usuario primero
        RegisterRequest registerReq = new RegisterRequest("loginuser@test.com", "password123");

        ResponseEntity<LoginResponse> registerRes = restTemplate.postForEntity(
                "/api/v1/auth/register",
                registerReq,
                LoginResponse.class
        );

        assertThat(registerRes.getStatusCode())
                .as("El registro previo debe ser exitoso")
                .isEqualTo(HttpStatus.CREATED);

        // 2. Login con las mismas credenciales
        LoginRequest loginReq = new LoginRequest("loginuser@test.com", "password123");

        ResponseEntity<LoginResponse> loginRes = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginReq,
                LoginResponse.class
        );

        // 3. Verificar que login retorna CUSTOMER
        assertThat(loginRes.getStatusCode())
                .as("El login exitoso debe retornar 200 OK")
                .isEqualTo(HttpStatus.OK);
        assertThat(loginRes.getBody())
                .as("El body del login no debe ser nulo")
                .isNotNull();
        assertThat(loginRes.getBody().role())
                .as("El campo role (deprecated) debe ser CUSTOMER")
                .isEqualTo("CUSTOMER");
        assertThat(loginRes.getBody().roles())
                .as("La lista roles debe contener CUSTOMER")
                .contains("CUSTOMER");
        assertThat(loginRes.getBody().accessToken())
                .as("El access token no debe ser nulo")
                .isNotNull();
        assertThat(loginRes.getBody().email())
                .as("El email debe coincidir")
                .isEqualTo("loginuser@test.com");
    }
}
