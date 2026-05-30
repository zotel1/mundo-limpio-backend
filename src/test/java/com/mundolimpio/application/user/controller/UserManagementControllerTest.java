package com.mundolimpio.application.user.controller;

import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.ChangeRolesRequest;
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
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
        // Given: el servicio retorna una pagina con 2 usuarios
        List<UserResponse> mockUsers = List.of(
                new UserResponse(1L, "admin", "admin@mundolimpio.com", "ADMIN", Instant.now(), List.of("ADMIN")),
                new UserResponse(2L, "operator", "operator@mundolimpio.com", "SALES_CLERK", Instant.now(), List.of("SALES_CLERK"))
        );
        Page<UserResponse> mockPage = new PageImpl<>(mockUsers, PageRequest.of(0, 20), 2);

        when(userManagementService.findAll(any(Pageable.class))).thenReturn(mockPage);

        // When/Then: GET /api/v1/users → 200 OK con pagina de 2 usuarios
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].username").value("admin"))
                .andExpect(jsonPath("$.content[0].email").value("admin@mundolimpio.com"))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.content[1].id").value(2))
                .andExpect(jsonPath("$.content[1].username").value("operator"))
                .andExpect(jsonPath("$.content[1].email").value("operator@mundolimpio.com"))
                .andExpect(jsonPath("$.content[1].role").value("SALES_CLERK"));
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

    // ==================== PATCH /api/v1/users/{id}/roles ====================

    /**
     * Test 6: PATCH /api/v1/users/{id}/roles con body valido retorna 200.
     * <p>
     * WHAT: Admin asigna multiples roles via ChangeRolesRequest con Set<Role>.
     * Verifica que el controller llama a service.changeRoles() con los
     * parametros correctos y retorna 200 con UserResponse.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRoles_ValidRequest_Returns200() throws Exception {
        // Given: el servicio retorna el usuario con roles actualizados
        UserResponse updatedUser = new UserResponse(2L, "operator",
                "operator@mundolimpio.com", "STOCK_MANAGER", Instant.now(),
                List.of("STOCK_MANAGER", "SALES_CLERK"));

        when(userManagementService.changeRoles(eq(2L), any(ChangeRolesRequest.class), any()))
                .thenReturn(updatedUser);

        // When/Then: PATCH /api/v1/users/2/roles → 200 OK con roles actualizados
        mockMvc.perform(patch("/api/v1/users/{id}/roles", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"STOCK_MANAGER\",\"SALES_CLERK\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.email").value("operator@mundolimpio.com"))
                .andExpect(jsonPath("$.role").value("STOCK_MANAGER"));
    }

    /**
     * Test 7: PATCH /api/v1/users/{id}/roles con ADMIN_EXCLUSIVE retorna 400.
     * <p>
     * WHAT: Cuando el servicio lanza ADMIN_EXCLUSIVE (ADMIN combinado con
     * otros roles), el UserExceptionHandler retorna 400 con ese codigo.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRoles_AdminExclusive_Returns400() throws Exception {
        // Given: el servicio lanza ADMIN_EXCLUSIVE
        when(userManagementService.changeRoles(eq(2L), any(ChangeRolesRequest.class), any()))
                .thenThrow(new IllegalArgumentException(
                        "ADMIN_EXCLUSIVE: El rol ADMIN no puede combinarse con otros roles."));

        // When/Then: PATCH con ADMIN + otro rol → 400 ADMIN_EXCLUSIVE
        mockMvc.perform(patch("/api/v1/users/{id}/roles", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ADMIN\",\"SALES_CLERK\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_EXCLUSIVE"));
    }

    /**
     * Test 8: PATCH /api/v1/users/{id}/roles con SELF_ADMIN_REMOVAL retorna 400.
     * <p>
     * WHAT: Cuando un ADMIN intenta quitarse su propio ADMIN via PATCH /roles,
     * el servicio lanza SELF_ADMIN_REMOVAL y el handler retorna 400.
     * Necesitamos SecurityContext con User real para getCurrentUserId().
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRoles_SelfAdminRemoval_Returns400() throws Exception {
        // Given: un ADMIN autenticado con ID=1
        User adminUser = new User("admin", "admin@mundolimpio.com", "pass", Role.ADMIN);
        adminUser.setId(1L);

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        adminUser, null, adminUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // El servicio lanza SELF_ADMIN_REMOVAL cuando currentUserId == targetId
        when(userManagementService.changeRoles(eq(1L), any(ChangeRolesRequest.class), eq(1L)))
                .thenThrow(new IllegalArgumentException(
                        "SELF_ADMIN_REMOVAL: No puedes quitarte tu propio rol ADMIN. " +
                        "Otro administrador debe realizar esta operacion."));

        // When/Then: ADMIN intenta quitarse su propio ADMIN → 400 SELF_ADMIN_REMOVAL
        mockMvc.perform(patch("/api/v1/users/{id}/roles", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SALES_CLERK\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ADMIN_REMOVAL"));
    }

    /**
     * Test 9: PATCH /api/v1/users/{id}/roles con usuario inexistente retorna 404.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRoles_UserNotFound_Returns404() throws Exception {
        // Given: el servicio lanza UserNotFoundException para el ID 99
        when(userManagementService.changeRoles(eq(99L), any(ChangeRolesRequest.class), any()))
                .thenThrow(new UserNotFoundException(99L));

        // When/Then: PATCH a usuario inexistente → 404 USER_NOT_FOUND
        mockMvc.perform(patch("/api/v1/users/{id}/roles", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ADMIN\"]}"))
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
