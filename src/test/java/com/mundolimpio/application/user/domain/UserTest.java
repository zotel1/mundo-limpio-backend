package com.mundolimpio.application.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios para la entidad User.
 * <p>
 * WHAT: Verifica que User cumple el contrato de Spring Security UserDetails
 * usando email como identificador de autenticación vía getUsername().
 * <p>
 * WHY: La especificación requiere que el JWT "sub" sea el email del usuario.
 * Sobrescribiendo getUsername() para devolver email, Spring Security y JwtService
 * usan email transparentemente sin modificar una sola línea de esos archivos.
 * El username original se preserva como display name vía getRawUsername().
 * <p>
 * DIFFERENCES: Anteriormente getUsername() devolvía el username real. Ahora
 * devuelve email para cumplir con el nuevo modelo de auth por email.
 */
class UserTest {

    // Constantes para triangulación con diferentes datos
    private static final String USERNAME_1 = "juan";
    private static final String EMAIL_1 = "juan@mail.com";
    private static final String PASSWORD_1 = "Pass1234";
    private static final Role ROLE_1 = Role.OPERATOR;

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
        // WHEN se crea un User con el nuevo constructor
        User user = new User(USERNAME_1, EMAIL_1, PASSWORD_1, ROLE_1);

        // THEN todos los campos están correctos
        assertThat(user.getRawUsername()).isEqualTo(USERNAME_1);
        assertThat(user.getEmail()).isEqualTo(EMAIL_1);
        assertThat(user.getUsername()).isEqualTo(EMAIL_1); // Spring Security contract
        assertThat(user.getPassword()).isEqualTo(PASSWORD_1);
        assertThat(user.getRole()).isEqualTo(ROLE_1);
        assertThat(user.getCreatedAt())
                .as("createdAt debe inicializarse automáticamente en el constructor")
                .isNotNull();
        assertThat(user.getId())
                .as("id debe ser null antes de persistencia JPA")
                .isNull();
    }
}
