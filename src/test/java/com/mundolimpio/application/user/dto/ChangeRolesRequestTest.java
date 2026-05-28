package com.mundolimpio.application.user.dto;

import com.mundolimpio.application.user.domain.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para ChangeRolesRequest — DTO de solicitud multi-rol.
 * <p>
 * WHAT: Verifica que el record acepta un Set de Role, lo expone via accessor,
 * y que la validacion Jakarta @NotNull rechaza sets nulos.
 * <p>
 * WHY: Este DTO reemplaza ChangeRoleRequest (un solo String) cuando el endpoint
 * PATCH /users/{id}/roles acepte multiples roles. Se testea aqui su construccion
 * y validacion para garantizar que el contrato con el cliente es correcto.
 */
class ChangeRolesRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Test: construccion con un solo rol — roles() retorna el Set correcto.
     */
    @Test
    void constructor_withSingleRole_returnsCorrectSet() {
        Set<Role> roles = Set.of(Role.SALES_CLERK);

        ChangeRolesRequest request = new ChangeRolesRequest(roles);

        assertNotNull(request);
        assertEquals(1, request.roles().size());
        assertTrue(request.roles().contains(Role.SALES_CLERK));
    }

    /**
     * Test: construccion con multiples roles — roles() retorna el Set completo.
     */
    @Test
    void constructor_withMultipleRoles_returnsCorrectSet() {
        Set<Role> roles = Set.of(Role.STOCK_MANAGER, Role.SALES_CLERK);

        ChangeRolesRequest request = new ChangeRolesRequest(roles);

        assertNotNull(request);
        assertEquals(2, request.roles().size());
        assertTrue(request.roles().contains(Role.STOCK_MANAGER));
        assertTrue(request.roles().contains(Role.SALES_CLERK));
    }

    /**
     * Test: validacion — roles null produce error de validacion.
     */
    @Test
    void validation_withNullRoles_reportsViolation() {
        ChangeRolesRequest request = new ChangeRolesRequest(null);

        var violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Debe haber al menos una violacion por roles nulo");
        assertTrue(violations.stream().anyMatch(
                v -> v.getPropertyPath().toString().equals("roles")));
    }

    /**
     * Test: validacion — roles vacio es valido estructuralmente
     * (la validacion de negocio — si debe tener al menos uno — va en el service).
     */
    @Test
    void validation_withEmptyRoles_isValid() {
        ChangeRolesRequest request = new ChangeRolesRequest(Set.of());

        var violations = validator.validate(request);

        assertTrue(violations.isEmpty(),
                "Set vacio es estructuralmente valido; reglas de negocio van en el service");
    }
}
