package com.mundolimpio.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * WHAT: Clase base para todos los tests de integración que usan PostgreSQL via Testcontainers.
 *
 * WHY: Antes usábamos H2 en memoria para tests, pero H2 no es 100% compatible con PostgreSQL
 * (diferencias en sintaxis DDL, tipos de datos, funciones). Testcontainers provee una instancia
 * real de PostgreSQL 16 en un contenedor Docker, asegurando que los tests se ejecutan contra
 * la misma base de datos que producción.
 *
 * HOW:
 * - @Testcontainers: Habilita la extensión JUnit Jupiter de Testcontainers.
 * - @Container static: El contenedor se comparte entre TODOS los tests (solo se inicia una vez).
 * - @DynamicPropertySource: Sobrescribe spring.datasource.* con las propiedades del contenedor.
 * - postgres:16-alpine: Imagen liviana (~40MB menos que la estándar), ideal para CI.
 *
 * DIFFERENCES con H2:
 * - H2: en memoria, sin Docker, rápido pero con diferencias de compatibilidad.
 * - Testcontainers PG: contenedor real, requiere Docker, más lento en arranque pero 100% compatible.
 * - Flyway corre las migraciones reales (antes estaba disabled en test).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
