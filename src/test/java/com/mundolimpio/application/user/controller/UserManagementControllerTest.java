package com.mundolimpio.application.user.controller;

import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.ChangeRoleRequest;
import com.mundolimpio.application.user.dto.ResetPasswordRequest;
import com.mundolimpio.application.user.dto.UserResponse;
import com.mundolimpio.application.user.exception.UserNotFoundException;
import com.mundolimpio.application.user.service.UserManagementService;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests para UserManagementController usando @SpringBootTest + @AutoConfigureMockMvc.
 *
 * QUÉ HACE: Verifica que los endpoints ADMIN de gestión de usuarios responden
 * correctamente a nivel HTTP: status codes, códigos de error, body JSON.
 * El servicio es mockeado para aislar la capa web.
 *
 * POR QUÉ @SpringBootTest en vez de @WebMvcTest:
 * - @WebMvcTest no carga SecurityConfig completo, y JwtAuthenticationFilter
 *   necesita JwtService que no está en el contexto reducido.
 * - InventoryControllerTest y AuthControllerTest ya establecen este patrón.
 *
 * DIFERENCIA con InventoryControllerTest:
 * - InventoryControllerTest usa @WithMockUser(roles="ADMIN") para todos los tests.
 * - UserManagementControllerTest usa @WithMockUser para la mayoría, pero el test
 *   de autodemoción (SELF_DEMOTION) requiere un principal de tipo User (dominio)
 *   para que getCurrentUserId() extraiga el ID correctamente. Por eso ese test
 *   setea SecurityContextHolder manualmente con un User real.
 *
 * DIFFERENCES: Antes usaba @SpringBootTest directo con H2; ahora extiende
 *              AbstractIntegrationTest que provee PostgreSQL via Testcontainers.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserManagementService userManagementService;

    /**
     * Limpia el SecurityContext después de cada test para evitar
     * que la configuración manual (ej: test de autodemoción) se
     * filtre a otros tests.
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== GET /api/v1/users ====================

    /**
     * Test 1: GET /api/v1/users con ADMIN retorna 200 con lista de usuarios.
     *
     * QUÉ VERIFICA:
     * - Service mockeado retorna 2 usuarios.
     * - Status 200 OK.
     * - Body es un array JSON con los usuarios esperados.
     * - No se exponen contraseñas (solo id, username, role, createdAt).
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void findAll_AsAdmin_Returns200WithUserList() throws Exception {
        // Given: el servicio retorna una lista con 2 usuarios
        List<UserResponse> mockUsers = List.of(
                new UserResponse(1L, "admin", "admin@mundolimpio.com", "ADMIN", Instant.now(), List.of("ADMIN")),
                new UserResponse(2L, "operator", "operator@mundolimpio.com", "SALES_CLERK", Instant.now(), List.of("SALES_CLERK"))
        );

        when(userManagementService.findAll()).thenReturn(mockUsers);

        // When/Then: GET /api/v1/users → 200 OK con array de 2 usuarios
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].email").value("admin@mundolimpio.com"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].username").value("operator"))
                .andExpect(jsonPath("$[1].email").value("operator@mundolimpio.com"))
                .andExpect(jsonPath("$[1].role").value("SALES_CLERK"));
    }

    /**
     * Test 2: GET /api/v1/users con OPERATOR retorna 403.
     *
     * QUÉ VERIFICA:
     * - @PreAuthorize("hasRole('ADMIN')") bloquea a usuarios con rol OPERATOR.
     * - Status 403 FORBIDDEN con código ACCESS_DENIED.
     *
     * POR QUÉ 403 y no 401:
     * - 401 = no autenticado (sin token).
     * - 403 = autenticado pero sin el rol requerido.
     * - @WithMockUser(roles = "OPERATOR") simula un usuario autenticado sin ADMIN.
     */
    @Test
    @WithMockUser(roles = "SALES_CLERK")
    void findAll_AsOperator_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * Test 3: GET /api/v1/users sin autenticación retorna 401.
     *
     * QUÉ VERIFICA:
     * - Sin token de autenticación, el endpoint protegido retorna 401.
     * - SecurityConfig define HttpStatusEntryPoint(UNAUTHORIZED) para no-autenticados.
     */
    @Test
    void findAll_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET /api/v1/users/{id} ====================

    /**
     * Test 4: GET /api/v1/users/{id} con ADMIN y usuario existente retorna 200.
     *
     * QUÉ VERIFICA:
     * - Service.findById() retorna UserResponse.
     * - Status 200 OK.
     * - Body contiene los campos del usuario en JSON.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void findById_WhenExists_Returns200() throws Exception {
        // Given: el servicio retorna un usuario existente
        UserResponse mockUser = new UserResponse(2L, "operator", "operator@mundolimpio.com", "SALES_CLERK", Instant.now(), List.of("SALES_CLERK"));

        when(userManagementService.findById(2L)).thenReturn(mockUser);

        // When/Then: GET /api/v1/users/2 → 200 OK con datos del usuario
        mockMvc.perform(get("/api/v1/users/{id}", 2L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.email").value("operator@mundolimpio.com"))
                .andExpect(jsonPath("$.role").value("SALES_CLERK"));
    }

    /**
     * Test 5: GET /api/v1/users/{id} con ADMIN y usuario inexistente retorna 404.
     *
     * QUÉ VERIFICA:
     * - Service.findById() lanza UserNotFoundException.
     * - UserExceptionHandler (o GlobalExceptionHandler fallback) retorna 404.
     * - Body contiene código "USER_NOT_FOUND".
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void findById_WhenNotExists_Returns404() throws Exception {
        // Given: el servicio lanza UserNotFoundException para el ID 99
        when(userManagementService.findById(99L))
                .thenThrow(new UserNotFoundException(99L));

        // When/Then: GET /api/v1/users/99 → 404 USER_NOT_FOUND
        mockMvc.perform(get("/api/v1/users/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with ID: 99"));
    }

    // ==================== PATCH /api/v1/users/{id}/role ====================

    /**
     * Test 6: PATCH /api/v1/users/{id}/role con body válido retorna 200.
     *
     * QUÉ VERIFICA:
     * - Controller extrae el ID del usuario autenticado del SecurityContext.
     * - Controller llama a service.changeRole() con los parámetros correctos.
     * - Status 200 OK con el UserResponse actualizado.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_ValidRequest_Returns200() throws Exception {
        // Given: el servicio retorna el usuario con rol actualizado
        UserResponse updatedUser = new UserResponse(2L, "operator", "operator@mundolimpio.com", "ADMIN", Instant.now(), List.of("ADMIN"));

        when(userManagementService.changeRole(eq(2L), eq("ADMIN"), any()))
                .thenReturn(updatedUser);

        // When/Then: PATCH /api/v1/users/2/role → 200 OK con rol actualizado
        mockMvc.perform(patch("/api/v1/users/{id}/role", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newRole\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.email").value("operator@mundolimpio.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    /**
     * Test 7: PATCH /api/v1/users/{id}/role con rol inválido retorna 400.
     *
     * QUÉ VERIFICA:
     * - Service.changeRole() lanza IllegalArgumentException("INVALID_ROLE:...").
     * - UserExceptionHandler captura y retorna 400 con código "INVALID_ROLE".
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_InvalidRole_Returns400() throws Exception {
        // Given: el servicio lanza INVALID_ROLE para un rol no válido
        when(userManagementService.changeRole(eq(2L), eq("INVALID"), any()))
                .thenThrow(new IllegalArgumentException(
                        "INVALID_ROLE: El rol 'INVALID' no es válido. Use ADMIN u OPERATOR."));

        // When/Then: PATCH con rol inválido → 400 INVALID_ROLE
        mockMvc.perform(patch("/api/v1/users/{id}/role", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newRole\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE"))
                .andExpect(jsonPath("$.message").value(
                        "INVALID_ROLE: El rol 'INVALID' no es válido. Use ADMIN u OPERATOR."));
    }

    /**
     * Test 8: PATCH /api/v1/users/{id}/role cuando ADMIN se autodemueve retorna 400.
     *
     * QUÉ VERIFICA:
     * - Controller extrae correctamente el ID del ADMIN del SecurityContext.
     * - Controller pasa currentUserId al service.
     * - Service lanza IllegalArgumentException("SELF_DEMOTION:...").
     * - UserExceptionHandler retorna 400 con código "SELF_DEMOTION".
     *
     * POR QUÉ usamos SecurityContextHolder manual en vez de @WithMockUser solo:
     * - @WithMockUser(roles="ADMIN") usa un String como principal ("user").
     * - getCurrentUserId() en el controller verifica
     *   authentication.getPrincipal() instanceof User (dominio).
     * - Con @WithMockUser, el principal NO es un User de dominio → getCurrentUserId()
     *   retorna null.
     * - Para probar la autodemoción necesitamos un principal de tipo User con ID real.
     * - Seteamos manualmente un UsernamePasswordAuthenticationToken con el dominio User
     *   como principal.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_SelfDemotion_Returns400() throws Exception {
        // Given: un ADMIN autenticado con ID=1 (usando User de dominio real)
        User adminUser = new User("admin", "admin@mundolimpio.com", "pass", Role.ADMIN);
        adminUser.setId(1L);

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        adminUser, null, adminUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // El servicio lanza SELF_DEMOTION cuando currentUserId == targetId
        when(userManagementService.changeRole(eq(1L), eq("OPERATOR"), eq(1L)))
                .thenThrow(new IllegalArgumentException(
                        "SELF_DEMOTION: No puedes cambiar tu propio rol. " +
                        "Otro administrador debe realizar esta operación."));

        // When/Then: ADMIN intenta cambiarse su propio rol → 400 SELF_DEMOTION
        mockMvc.perform(patch("/api/v1/users/{id}/role", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newRole\":\"OPERATOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_DEMOTION"));
    }

    /**
     * Test 9: PATCH /api/v1/users/{id}/role con usuario inexistente retorna 404.
     *
     * QUÉ VERIFICA:
     * - Service.changeRole() lanza UserNotFoundException.
     * - Status 404 NOT_FOUND con código "USER_NOT_FOUND".
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_UserNotFound_Returns404() throws Exception {
        // Given: el servicio lanza UserNotFoundException para el ID 99
        when(userManagementService.changeRole(eq(99L), anyString(), any()))
                .thenThrow(new UserNotFoundException(99L));

        // When/Then: PATCH a usuario inexistente → 404 USER_NOT_FOUND
        mockMvc.perform(patch("/api/v1/users/{id}/role", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newRole\":\"ADMIN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with ID: 99"));
    }

    // ==================== PATCH /api/v1/users/{id}/password ====================

    /**
     * Test 10: PATCH /api/v1/users/{id}/password con body válido retorna 200.
     *
     * QUÉ VERIFICA:
     * - Service.resetPassword() retorna UserResponse.
     * - Status 200 OK.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void resetPassword_ValidRequest_Returns200() throws Exception {
        // Given: el servicio retorna el usuario con contraseña reseteada
        UserResponse userResponse = new UserResponse(2L, "operator", "operator@mundolimpio.com", "SALES_CLERK", Instant.now(), List.of("SALES_CLERK"));

        when(userManagementService.resetPassword(eq(2L), eq("NewPass123")))
                .thenReturn(userResponse);

        // When/Then: PATCH /api/v1/users/2/password → 200 OK
        mockMvc.perform(patch("/api/v1/users/{id}/password", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.email").value("operator@mundolimpio.com"))
                .andExpect(jsonPath("$.username").value("operator"));
    }

    /**
     * Test 11: PATCH /api/v1/users/{id}/password con contraseña muy corta retorna 400.
     *
     * QUÉ VERIFICA:
     * - Jakarta @Size(min=6) en ResetPasswordRequest.newPassword rechaza "ab".
     * - MethodArgumentNotValidException → GlobalExceptionHandler → 400 VALIDATION_ERROR.
     *
     * DIFERENCIA con la especificación:
     * - La especificación dice "400 INVALID_PASSWORD" para este caso.
     * - Pero la validación Jakarta (@Size(min=6)) ocurre ANTES de llegar al servicio,
     *   por lo que el código de error es "VALIDATION_ERROR" (no INVALID_PASSWORD).
     * - INVALID_PASSWORD sería el código si el servicio lanzara la excepción,
     *   pero la validación estructural ocurre en el DTO antes.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void resetPassword_TooShortPassword_Returns400() throws Exception {
        // When/Then: PATCH con contraseña de solo 2 chars → 400 VALIDATION_ERROR
        mockMvc.perform(patch("/api/v1/users/{id}/password", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * Test 12: PATCH /api/v1/users/{id}/password con usuario inexistente retorna 404.
     *
     * QUÉ VERIFICA:
     * - Service.resetPassword() lanza UserNotFoundException.
     * - Status 404 NOT_FOUND con código "USER_NOT_FOUND".
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void resetPassword_UserNotFound_Returns404() throws Exception {
        // Given: el servicio lanza UserNotFoundException para el ID 99
        when(userManagementService.resetPassword(eq(99L), anyString()))
                .thenThrow(new UserNotFoundException(99L));

        // When/Then: PATCH a usuario inexistente → 404 USER_NOT_FOUND
        mockMvc.perform(patch("/api/v1/users/{id}/password", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with ID: 99"));
    }
}
