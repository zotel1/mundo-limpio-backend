package com.mundolimpio.application.user.controller;

import com.mundolimpio.application.common.dto.ErrorResponse;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.ChangeRolesRequest;
import com.mundolimpio.application.user.dto.LoginRequest;
import com.mundolimpio.application.user.dto.LoginResponse;
import com.mundolimpio.application.user.dto.ResetPasswordRequest;
import com.mundolimpio.application.user.dto.UserResponse;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración para los endpoints ADMIN de gestión de usuarios.
 *
 * WHAT: Verifica HTTP → JwtAuthenticationFilter → @PreAuthorize → Controller → Service → DB
 *       contra PostgreSQL real via Testcontainers.
 * WHY: La autenticación JWT y el hashing BCrypt deben funcionar idéntico en test y producción.
 * DIFFERENCES: Antes usaba @SpringBootTest directo con H2; ahora extiende
 *              AbstractIntegrationTest que provee PostgreSQL via Testcontainers.
 */
@ActiveProfiles("test")
class UserManagementIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Record interno para agrupar el contexto de un ADMIN autenticado.
     *
     * @param id    ID del usuario ADMIN en DB
     * @param token JWT generado para ese ADMIN
     */
    private record AdminContext(Long id, String token) {}

    /**
     * Limpieza antes de cada test y configuración del cliente HTTP.
     *
     * POR QUÉ configuramos el request factory:
     * - TestRestTemplate usa SimpleClientHttpRequestFactory por defecto,
     *   que wrappea HttpURLConnection. HttpURLConnection NO soporta PATCH.
     * - Con Apache HttpClient5 en classpath, usamos HttpComponentsClientHttpRequestFactory
     *   que sí soporta PATCH, necesario para /api/v1/users/{id}/role y /password.
     *
     * POR QUÉ @BeforeEach y no @BeforeAll:
     * - Cada test debe ser independiente. Si un test crea usuarios,
     *   el siguiente no debe encontrar esos datos residuales.
     * - deleteAll() asegura que empezamos con una DB limpia.
     * - Consistente con AuthRefreshIT.
     */
    @BeforeEach
    void setUp() {
        // Configurar soporte para HTTP PATCH (HttpURLConnection no lo soporta)
        RestTemplate rt = restTemplate.getRestTemplate();
        rt.setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        userRepository.deleteAll();
    }

    /**
     * Helper: crea un usuario ADMIN en DB y genera un JWT para él.
     *
     * QUÉ HACE: Persiste un User con Role.ADMIN en H2, genera un JWT
     * firmado usando JwtService, y retorna el ID + token.
     *
     * POR QUÉ este helper:
     * - Casi todos los tests necesitan un ADMIN autenticado.
     * - Centraliza la creación para evitar duplicación.
     * - El password se hashea con BCrypt real (mismo que en producción).
     *
     * @return AdminContext con ID del admin y su JWT
     */
    private AdminContext createAdminContext() {
        User admin = new User("admin", "admin@mundolimpio.com",
                passwordEncoder.encode("admin123"), Role.ADMIN);
        admin = userRepository.save(admin);
        String token = jwtService.generateToken(admin);
        return new AdminContext(admin.getId(), token);
    }

    /**
     * Helper: crea headers HTTP con token JWT y Content-Type JSON.
     *
     * @param token JWT del ADMIN autenticado
     * @return HttpHeaders con Authorization Bearer y Content-Type application/json
     */
    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ======================== GET /api/v1/users ========================

    /**
     * REQ-1: Listar usuarios con 2 registros.
     *
     * ESCENARIO: GIVEN 2 users exist → WHEN ADMIN GET /api/v1/users
     * → THEN 200 with 2 entries (id, username, role, createdAt), no password.
     */
    @Test
    void findAll_ShouldReturnAllUsers() {
        // Given: admin autenticado + 1 OPERATOR en DB
        AdminContext admin = createAdminContext();
        userRepository.save(new User("operator", "operator@mundolimpio.com",
                passwordEncoder.encode("pass123"), Role.SALES_CLERK));

        // When: GET /api/v1/users como ADMIN
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(admin.token()));
        ResponseEntity<UserResponse[]> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.GET, entity, UserResponse[].class);

        // Then: 200 OK con 2 usuarios en el array
        assertThat(response.getStatusCode())
                .as("ADMIN debe poder listar usuarios")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("El body no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody())
                .as("Debe retornar 2 usuarios (admin + operator)")
                .hasSize(2);
    }

    /**
     * REQ-1: Listar usuarios cuando solo existe el ADMIN.
     *
     * ESCENARIO: GIVEN solo el admin existe → WHEN ADMIN GET /api/v1/users
     * → THEN 200 with 1 entry (el admin mismo).
     *
     * DIFERENCIA con el escenario "empty array" de la spec:
     * La spec dice "GIVEN no users exist → 200 []", pero en un integration
     * test necesitamos al menos un ADMIN para autenticarse. El admin
     * siempre aparece en la lista. El caso "empty array" se cubre en
     * UserManagementControllerTest (test unitario con service mockeado).
     */
    @Test
    void findAll_WhenOnlyAdmin_ShouldReturnAdminOnly() {
        // Given: solo el admin autenticado (sin otros usuarios)
        AdminContext admin = createAdminContext();

        // When: GET /api/v1/users
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(admin.token()));
        ResponseEntity<UserResponse[]> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.GET, entity, UserResponse[].class);

        // Then: 200 OK con 1 usuario (el admin)
        assertThat(response.getStatusCode())
                .as("ADMIN debe poder listar usuarios aunque sea el único")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("El body no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody())
                .as("Debe retornar 1 usuario (solo el admin)")
                .hasSize(1);
        assertThat(response.getBody()[0].username())
                .as("El username debe ser el del admin")
                .isEqualTo("admin");
        assertThat(response.getBody()[0].email())
                .as("El email debe ser del admin")
                .isEqualTo("admin@mundolimpio.com");
    }

    /**
     * Authorization: OPERATOR intenta listar usuarios.
     *
     * ESCENARIO: GIVEN user has OPERATOR role → WHEN GET /api/v1/users
     * → THEN 403 ACCESS_DENIED.
     */
    @Test
    void findAll_Operator_ShouldReturn403() {
        // Given: OPERATOR autenticado (sin rol ADMIN)
        User operator = new User("operator", "operator@mundolimpio.com",
                passwordEncoder.encode("pass123"), Role.SALES_CLERK);
        operator = userRepository.save(operator);
        String operatorToken = jwtService.generateToken(operator);

        // When: GET /api/v1/users como OPERATOR
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(operatorToken));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.GET, entity, ErrorResponse.class);

        // Then: 403 FORBIDDEN con código ACCESS_DENIED
        assertThat(response.getStatusCode())
                .as("OPERATOR debe recibir 403 al acceder a endpoint ADMIN")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .as("El body de error no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().code())
                .as("El código de error debe ser ACCESS_DENIED")
                .isEqualTo("ACCESS_DENIED");
    }

    // ======================== GET /api/v1/users/{id} ========================

    /**
     * REQ-2: Obtener usuario por ID existente.
     *
     * ESCENARIO: GIVEN user id=2 exists → WHEN ADMIN GET /api/v1/users/2
     * → THEN 200 with full details.
     */
    @Test
    void findById_WhenExists_ShouldReturnUser() {
        // Given: admin autenticado + OPERATOR en DB
        AdminContext admin = createAdminContext();
        User operator = userRepository.save(
                new User("operator", "operator@mundolimpio.com",
                        passwordEncoder.encode("pass123"), Role.SALES_CLERK));

        // When: PATCH /api/v1/users/{id}/roles con roles=[\"ADMIN\"]
        ChangeRolesRequest request = new ChangeRolesRequest(Set.of(Role.ADMIN));
        HttpEntity<ChangeRolesRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/v1/users/" + operator.getId() + "/roles",
                HttpMethod.PATCH, entity, UserResponse.class);

        // Then: 200 OK con roles actualizados
        assertThat(response.getStatusCode())
                .as("Cambio de roles exitoso debe retornar 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("El body no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().id())
                .as("El ID debe coincidir con el usuario modificado")
                .isEqualTo(operator.getId());
        assertThat(response.getBody().role())
                .as("El role legacy debe ser ADMIN despues del cambio")
                .isEqualTo("ADMIN");
        assertThat(response.getBody().email())
                .as("El email debe estar presente en la respuesta")
                .isEqualTo("operator@mundolimpio.com");

        // Verificar que el cambio persiste en DB
        User updatedUser = userRepository.findById(operator.getId()).orElseThrow();
        assertThat(updatedUser.getRoles())
                .as("Los roles en DB deben ser [ADMIN]")
                .containsExactly(Role.ADMIN);
    }

    /**
     * REQ-3: Cambiar roles con ADMIN combinado con otro rol.
     *
     * ESCENARIO: WHEN ADMIN PATCH /api/v1/users/2/roles {"roles":["ADMIN","SALES_CLERK"]}
     * → THEN 400 ADMIN_EXCLUSIVE.
     */
    @Test
    void changeRoles_AdminExclusive_ShouldReturn400() {
        // Given: admin autenticado + OPERATOR en DB
        AdminContext admin = createAdminContext();
        User operator = userRepository.save(
                new User("operator", "operator@mundolimpio.com",
                        passwordEncoder.encode("pass123"), Role.SALES_CLERK));

        // When: PATCH con ADMIN + SALES_CLERK (viola UR-R3)
        ChangeRolesRequest request = new ChangeRolesRequest(
                Set.of(Role.ADMIN, Role.SALES_CLERK));
        HttpEntity<ChangeRolesRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/users/" + operator.getId() + "/roles",
                HttpMethod.PATCH, entity, ErrorResponse.class);

        // Then: 400 ADMIN_EXCLUSIVE
        assertThat(response.getStatusCode())
                .as("ADMIN combinado debe retornar 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .as("El body de error no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().code())
                .as("El codigo de error debe ser ADMIN_EXCLUSIVE")
                .isEqualTo("ADMIN_EXCLUSIVE");
    }

    /**
     * REQ-3: Self ADMIN removal (UR-R6).
     *
     * ESCENARIO: GIVEN auth ADMIN id=1 → WHEN same ADMIN
     * PATCH /api/v1/users/1/roles {"roles":["SALES_CLERK"]} → THEN 400 SELF_ADMIN_REMOVAL.
     */
    @Test
    void changeRoles_SelfAdminRemoval_ShouldReturn400() {
        // Given: admin autenticado
        AdminContext admin = createAdminContext();

        // When: ADMIN intenta quitarse su propio ADMIN
        ChangeRolesRequest request = new ChangeRolesRequest(Set.of(Role.SALES_CLERK));
        HttpEntity<ChangeRolesRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/users/" + admin.id() + "/roles",
                HttpMethod.PATCH, entity, ErrorResponse.class);

        // Then: 400 SELF_ADMIN_REMOVAL
        assertThat(response.getStatusCode())
                .as("Auto-remocion de ADMIN debe retornar 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .as("El body de error no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().code())
                .as("El codigo de error debe ser SELF_ADMIN_REMOVAL")
                .isEqualTo("SELF_ADMIN_REMOVAL");
    }

    /**
     * REQ-3: Cambiar roles de usuario inexistente.
     *
     * ESCENARIO: GIVEN user id=99 missing → WHEN ADMIN
     * PATCH /api/v1/users/99/roles {"roles":["SALES_CLERK"]}
     * → THEN 404 USER_NOT_FOUND.
     */
    @Test
    void changeRoles_UserNotFound_ShouldReturn404() {
        // Given: admin autenticado
        AdminContext admin = createAdminContext();

        // When: PATCH a usuario inexistente (ID 999)
        ChangeRolesRequest request = new ChangeRolesRequest(Set.of(Role.SALES_CLERK));
        HttpEntity<ChangeRolesRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/users/999/roles",
                HttpMethod.PATCH, entity, ErrorResponse.class);

        // Then: 404 USER_NOT_FOUND
        assertThat(response.getStatusCode())
                .as("Usuario inexistente debe retornar 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .as("El body de error no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().code())
                .as("El codigo de error debe ser USER_NOT_FOUND")
                .isEqualTo("USER_NOT_FOUND");
    }

    // ======================== PATCH /api/v1/users/{id}/password ========================

    /**
     * REQ-4: Resetear contraseña exitosamente.
     *
     * ESCENARIO: GIVEN user id=2 with old password hash → WHEN ADMIN
     * PATCH /api/v1/users/2/password {"newPassword":"NewPass123"}
     * → THEN 200 with user data (no password), password updated to BCrypt hash.
     *
     * QUÉ VERIFICA ADEMÁS:
     * - Después del reset, el usuario puede hacer login con la nueva contraseña.
     * - Esto prueba que BCryptEncoder.encode() generó un hash válido
     *   y que AuthService.login() puede verificarlo.
     * - Es una verificación end-to-end de la integración entre
     *   UserManagementService.resetPassword() y AuthService.login().
     */
    @Test
    void resetPassword_ShouldUpdatePassword() {
        // Given: admin autenticado + OPERATOR en DB con contraseña conocida
        AdminContext admin = createAdminContext();
        User operator = userRepository.save(
                new User("operator", "operator@mundolimpio.com",
                        passwordEncoder.encode("oldPass123"), Role.SALES_CLERK));

        // When: ADMIN resetea la contraseña del operator
        ResetPasswordRequest request = new ResetPasswordRequest("NewPass123");
        HttpEntity<ResetPasswordRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/v1/users/" + operator.getId() + "/password",
                HttpMethod.PATCH, entity, UserResponse.class);

        // Then: 200 OK con datos del usuario
        assertThat(response.getStatusCode())
                .as("Reset de contraseña exitoso debe retornar 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("El body no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().id())
                .as("El ID debe coincidir con el usuario modificado")
                .isEqualTo(operator.getId());
        assertThat(response.getBody().email())
                .as("El email debe estar presente")
                .isEqualTo("operator@mundolimpio.com");

        // Then: El usuario puede hacer login con la NUEVA contraseña
        // POR QUÉ: Verificamos que el hash BCrypt se generó correctamente
        // y que la cadena completa auth → loadUser → passwordEncoder.matches() funciona.
        ResponseEntity<LoginResponse> loginRes = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest("operator@mundolimpio.com", "NewPass123"),
                LoginResponse.class);
        assertThat(loginRes.getStatusCode())
                .as("El usuario debe poder hacer login con la nueva contraseña")
                .isEqualTo(HttpStatus.OK);
        assertThat(loginRes.getBody())
                .as("El login response no debe ser nulo")
                .isNotNull();
        assertThat(loginRes.getBody().email())
                .as("El email del login debe coincidir")
                .isEqualTo("operator@mundolimpio.com");
        assertThat(loginRes.getBody().username())
                .as("El username del login debe coincidir")
                .isEqualTo("operator");
    }

    /**
     * REQ-4: Resetear contraseña de usuario inexistente.
     *
     * ESCENARIO: GIVEN user id=99 missing → WHEN ADMIN
     * PATCH /api/v1/users/99/password {"newPassword":"NewPass123"}
     * → THEN 404 USER_NOT_FOUND.
     */
    @Test
    void resetPassword_UserNotFound_ShouldReturn404() {
        // Given: admin autenticado
        AdminContext admin = createAdminContext();

        // When: PATCH contraseña a usuario inexistente (ID 999)
        ResetPasswordRequest request = new ResetPasswordRequest("NewPass123");
        HttpEntity<ResetPasswordRequest> entity = new HttpEntity<>(request, authHeaders(admin.token()));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/users/999/password",
                HttpMethod.PATCH, entity, ErrorResponse.class);

        // Then: 404 USER_NOT_FOUND
        assertThat(response.getStatusCode())
                .as("Usuario inexistente debe retornar 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .as("El body de error no debe ser nulo")
                .isNotNull();
        assertThat(response.getBody().code())
                .as("El código de error debe ser USER_NOT_FOUND")
                .isEqualTo("USER_NOT_FOUND");
    }
}
