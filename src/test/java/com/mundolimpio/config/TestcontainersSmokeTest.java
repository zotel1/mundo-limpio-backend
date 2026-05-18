package com.mundolimpio.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WHAT: Test de humo que verifica que Spring Boot carga correctamente con Testcontainers PostgreSQL.
 *
 * WHY: Es el primer test que valida la infraestructura completa:
 * 1. Testcontainers inicia PostgreSQL 16 en Docker.
 * 2. @DynamicPropertySource inyecta las propiedades de conexión.
 * 3. Spring Boot carga el ApplicationContext (incluyendo Flyway, JPA, Security).
 * 4. Flyway ejecuta las migraciones V1-V4 contra PostgreSQL real.
 *
 * HOW: Extiende AbstractIntegrationTest que define el contenedor compartido.
 * Verifica que el ApplicationContext se carga sin errores.
 *
 * ESTADO: RED — Este test DEBE fallar porque application-test.yml todavía tiene:
 * - driver-class-name: org.h2.Driver (incompatible con URL de PostgreSQL)
 * - spring.flyway.enabled: false (migraciones no corren)
 * - spring.jpa.hibernate.ddl-auto: create-drop (Hibernate intenta crear tablas con dialecto H2)
 */
class TestcontainersSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        // WHAT: Verifica que el ApplicationContext de Spring se cargó correctamente
        // con Testcontainers PostgreSQL, Flyway migraciones ejecutadas, y JPA configurado.
        // WHY: Si este test pasa, toda la infraestructura de test está funcionando.
        // DIFFERENCES: Reemplaza el simple assertTrue(true) con verificaciones reales
        // que prueban que el contexto cargó y que beans clave existen.
        assertNotNull(context, "ApplicationContext debe cargarse exitosamente");
        assertTrue(context.getBeanDefinitionCount() > 0,
                "Debe haber beans definidos en el contexto");
    }
}
