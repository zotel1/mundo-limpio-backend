package com.mundolimpio.application.user.domain;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios para la entidad User con soporte multi-rol.
 * <p>
 * WHAT: Verifica que User cumple el contrato de Spring Security UserDetails
 * con multiples roles, usando email como identificador de autenticación vía getUsername().
 * <p>
 * WHY: El modelo RBAC ahora soporta 6 roles con asignacion multiple.
 * getAuthorities() debe devolver todas las autoridades del Set<Role>.
 * getUsername() sigue devolviendo email (contrato Spring Security).
 * <p>
 * DIFFERENCES: Antes User tenia un solo Role (getRole()). Ahora tiene
 * Set<Role> (getRoles()). getRole() queda deprecated devolviendo el primero.
 * Se agrega constructor varargs Role... para asignacion multiple.
 */
class UserTest {

    // Constantes para triangulación con diferentes datos
    private static final String USERNAME_1 = "juan";
    private static final String EMAIL_1 = "juan@mail.com";
    private static final String PASSWORD_1 = "Pass1234";
    private static final Role ROLE_1 = Role.SALES_CLERK;

    private static final String USERNAME_2 = "maria-xk9";
    private static final String EMAIL_2 = "maria@otro.com";
    private static final String PASSWORD_2 = "SecurePass!99";
    private static final Role ROLE_2 = Role.ADMIN;

    /**
     * Test 1.1.1: getUsername() debe devolver el email, no el username real.
     * <p>
     * Este es el cambio de contrato MÁS IMPORTANTE: Spring Security llama a
     * getUsername() para identificar al usuario. Necesitamos que devuelva email
     * para que JWT, filtros y autenticación funcionen por email sin modificar
     * esos componentes.
     */
    @Test
    void testGetUsername_ReturnsEmail() {
        // GIVEN un User creado con username y email distintos
        User user1 = new User(USERNAME_1, EMAIL_1, PASSWORD_1, ROLE_1);
        User user2 = new User(USERNAME_2, EMAIL_2, PASSWORD_2, ROLE_2);

        // WHEN se llama a getUsername()
        // THEN devuelve el email, NO el username real
        assertThat(user1.getUsername())
                .as("getUsername() debe devolver email como identificador de Spring Security")
                .isEqualTo(EMAIL_1)
                .isNotEqualTo(USERNAME_1);

        // Triangulación: segundo usuario con datos diferentes
        assertThat(user2.getUsername())
                .isEqualTo(EMAIL_2)
                .isNotEqualTo(USERNAME_2);
    }

    /**
     * Test 1.1.2: getRawUsername() debe devolver el username real (display name).
     * <p>
     * Como getUsername() ahora devuelve email, necesitamos un acceso separado
     * para el username auto-generado que se muestra en respuestas y admin UI.
     */
    @Test
    void testGetRawUsername_ReturnsUsername() {
        // GIVEN un User con username auto-generado y email
        User user1 = new User(USERNAME_1, EMAIL_1, PASSWORD_1, ROLE_1);
        User user2 = new User(USERNAME_2, EMAIL_2, PASSWORD_2, ROLE_2);

        // WHEN se llama a getRawUsername()
        // THEN devuelve el username real (display name)
        assertThat(user1.getRawUsername())
                .as("getRawUsername() debe devolver el username original como display name")
                .isEqualTo(USERNAME_1)
                .isNotEqualTo(EMAIL_1);

        // Triangulación: username con sufijo aleatorio por colisión
        assertThat(user2.getRawUsername())
                .isEqualTo(USERNAME_2)
                .isNotEqualTo(EMAIL_2);
    }

    /**
     * Test 1.1.3: getEmail() debe devolver el email del usuario.
     * <p>
     * Acceso directo al campo email para uso en DTOs, mappers y servicios
     * que necesitan el email sin pasar por getUsername().
     */
    @Test
    void testGetEmail_ReturnsEmail() {
        // GIVEN un User con email
        User user1 = new User(USERNAME_1, EMAIL_1, PASSWORD_1, ROLE_1);
        User user2 = new User(USERNAME_2, EMAIL_2, PASSWORD_2, ROLE_2);

        // WHEN se llama a getEmail()
        // THEN devuelve el email
        assertThat(user1.getEmail())
                .as("getEmail() debe devolver el email del usuario")
                .isEqualTo(EMAIL_1);

        // Triangulación: segundo email
        assertThat(user2.getEmail())
                .isEqualTo(EMAIL_2);
    }

    /**
     * Test 1.1.4: El constructor con 4 parámetros debe setear todos los campos
     * correctamente (username, email, password, role) y createdAt automático.
     */
    @Test
    void testConstructor_SetsAllFields() {
        // WHEN se crea un User con el nuevo constructor (varargs Role...)
        User user = new User(USERNAME_1, EMAIL_1, PASSWORD_1, ROLE_1);

        // THEN todos los campos están correctos
        assertThat(user.getRawUsername()).isEqualTo(USERNAME_1);
        assertThat(user.getEmail()).isEqualTo(EMAIL_1);
        assertThat(user.getUsername()).isEqualTo(EMAIL_1); // Spring Security contract
        assertThat(user.getPassword()).isEqualTo(PASSWORD_1);
        assertThat(user.getRoles())
                .as("getRoles() debe contener el rol asignado en el constructor")
                .containsExactly(ROLE_1);
        assertThat(user.getCreatedAt())
                .as("createdAt debe inicializarse automáticamente en el constructor")
                .isNotNull();
        assertThat(user.getId())
                .as("id debe ser null antes de persistencia JPA")
                .isNull();
    }

    // ======================== NUEVOS TESTS MULTI-ROL ========================

    /**
     * Test 2.1: getRoles() debe devolver todos los roles asignados.
     * <p>
     * WHAT: Verifica que el Set<Role> contiene exactamente los roles
     * pasados al constructor varargs.
     * WHY: UR-R2: un usuario puede tener múltiples roles simultáneamente.
     * El User debe preservar todos los roles asignados.
     */
    @Test
    void testGetRoles_ReturnsAllAssignedRoles() {
        // GIVEN un User creado con múltiples roles vía varargs
        User user = new User("carlos", "carlos@mail.com", "pw123",
                Role.STOCK_MANAGER, Role.SALES_CLERK);

        // WHEN se llama a getRoles()
        Set<Role> roles = user.getRoles();

        // THEN contiene todos los roles asignados
        assertThat(roles)
                .as("getRoles() debe devolver todos los roles asignados en el constructor")
                .isNotEmpty()
                .hasSize(2)
                .containsExactlyInAnyOrder(Role.STOCK_MANAGER, Role.SALES_CLERK);
    }

    /**
     * Test 2.2: getRoles() con un solo rol (backward compat vía varargs).
     * <p>
     * WHAT: Verifica que el constructor varargs también funciona con un solo rol.
     * WHY: El constructor usa Role... para que new User(..., Role.ADMIN) siga
     * compilando sin cambios en código existente.
     */
    @Test
    void testGetRoles_SingleRoleViaVarargs() {
        // GIVEN un User creado con un solo rol vía varargs
        User user = new User("ana", "ana@mail.com", "pw456", Role.ADMIN);

        // WHEN se llama a getRoles()
        Set<Role> roles = user.getRoles();

        // THEN contiene exactamente ese rol
        assertThat(roles)
                .as("getRoles() debe funcionar con un solo rol via varargs")
                .hasSize(1)
                .containsExactly(Role.ADMIN);
    }

    /**
     * Test 2.3: getRoles() con cero roles (usuario nuevo sin asignar).
     * <p>
     * WHY: UR-R4: usuarios nuevos se registran sin roles. El admin asigna
     * roles posteriormente via PATCH /users/{id}/roles.
     */
    @Test
    void testGetRoles_EmptyWhenNoRolesAssigned() {
        // GIVEN un User creado sin roles (no-args constructor + setter)
        User user = new User();
        user.setUsername("nuevo");
        user.setEmail("nuevo@mail.com");
        user.setPassword("pw789");

        // WHEN se llama a getRoles()
        Set<Role> roles = user.getRoles();

        // THEN el Set está vacío, no null
        assertThat(roles)
                .as("getRoles() debe devolver Set vacio (no null) cuando no hay roles")
                .isNotNull()
                .isEmpty();
    }

    /**
     * Test 2.4: getAuthorities() debe devolver todas las autoridades
     * correspondientes a todos los roles asignados.
     * <p>
     * WHAT: Verifica que cada Role en el Set se mapea a un SimpleGrantedAuthority
     * con prefijo "ROLE_" y que no hay duplicados.
     * WHY: UR-R2: Spring Security evalúa hasAnyRole() sobre la unión de
     * todas las autoridades. getAuthorities() debe reflejar la unión completa.
     */
    @Test
    void testGetAuthorities_ReturnsAllRoleAuthorities() {
        // GIVEN un User con múltiples roles
        User user = new User("diego", "diego@mail.com", "pw000",
                Role.STOCK_MANAGER, Role.PRODUCTION_OP);

        // WHEN se llama a getAuthorities()
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        // THEN contiene una autoridad por cada rol, sin duplicados
        assertThat(authorities)
                .as("getAuthorities() debe devolver todas las autoridades de todos los roles")
                .isNotNull()
                .hasSize(2)
                .extracting(GrantedAuthority::getAuthority)
                .as("Las autoridades deben tener prefijo ROLE_")
                .containsExactlyInAnyOrder("ROLE_STOCK_MANAGER", "ROLE_PRODUCTION_OP");
    }

    /**
     * Test 2.5: getAuthorities() con un solo rol (backward compat).
     * <p>
     * Triangulación: verifica que el caso de un solo rol también
     * funciona correctamente con la nueva implementación basada en stream.
     */
    @Test
    void testGetAuthorities_SingleRole() {
        // GIVEN un User con un solo rol
        User user = new User("elena", "elena@mail.com", "pw111", Role.ADMIN);

        // WHEN se llama a getAuthorities()
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        // THEN una sola autoridad ROLE_ADMIN
        assertThat(authorities)
                .as("getAuthorities() debe devolver una autoridad para un solo rol")
                .hasSize(1)
                .first()
                .extracting(GrantedAuthority::getAuthority)
                .isEqualTo("ROLE_ADMIN");
    }

    /**
     * Test 2.6: getAuthorities() sin roles devuelve colección vacía.
     * <p>
     * WHY: Un usuario sin roles no debe tener ninguna autoridad.
     * Spring Security tratara esto como acceso denegado a todo.
     */
    @Test
    void testGetAuthorities_EmptyWhenNoRoles() {
        // GIVEN un User sin roles
        User user = new User();
        user.setUsername("sinrol");
        user.setEmail("sinrol@mail.com");
        user.setPassword("pw");

        // WHEN se llama a getAuthorities()
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        // THEN colección vacía (no null)
        assertThat(authorities)
                .as("getAuthorities() debe devolver coleccion vacia cuando no hay roles")
                .isNotNull()
                .isEmpty();
    }

    /**
     * Test 2.7: getRole() deprecated debe devolver el primer rol.
     * <p>
     * WHAT: Verifica backward compat: getRole() devuelve el primer rol
     * del Set para código legacy que aún no migró a getRoles().
     * WHY: Minimizar breakage en tests y servicios durante la transición.
     */
    @Test
    void testGetRole_Deprecated_ReturnsFirstRole() {
        // GIVEN un User con múltiples roles
        User user = new User("pablo", "pablo@mail.com", "pw222",
                Role.STOCK_MANAGER, Role.SALES_CLERK);

        // WHEN se llama a getRole() (deprecated)
        Role firstRole = user.getRole();

        // THEN devuelve algún rol del Set (no null)
        assertThat(firstRole)
                .as("getRole() deprecated debe devolver algun rol del Set")
                .isNotNull()
                .isIn(user.getRoles());
    }

    /**
     * Test 2.8: getRole() deprecated devuelve null cuando no hay roles.
     */
    @Test
    void testGetRole_Deprecated_ReturnsNullWhenNoRoles() {
        // GIVEN un User sin roles
        User user = new User();
        user.setUsername("sinrol2");
        user.setEmail("sinrol2@mail.com");
        user.setPassword("pw");

        // WHEN se llama a getRole() (deprecated)
        Role role = user.getRole();

        // THEN es null (sin roles)
        assertThat(role)
                .as("getRole() deprecated debe devolver null cuando no hay roles")
                .isNull();
    }

    /**
     * Test 2.9: setRoles() permite reemplazar todos los roles.
     * <p>
     * WHAT: Verifica que el nuevo setter setRoles(Set<Role>) reemplaza
     * completamente los roles anteriores.
     */
    @Test
    void testSetRoles_ReplacesAllRoles() {
        // GIVEN un User con roles iniciales
        User user = new User("laura", "laura@mail.com", "pw333",
                Role.SALES_CLERK);

        // WHEN se reemplazan los roles
        user.setRoles(Set.of(Role.ADMIN));

        // THEN solo contiene los nuevos roles
        assertThat(user.getRoles())
                .as("setRoles() debe reemplazar completamente los roles anteriores")
                .hasSize(1)
                .containsExactly(Role.ADMIN);

        // Y getAuthorities() refleja los nuevos roles
        assertThat(user.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }
}
