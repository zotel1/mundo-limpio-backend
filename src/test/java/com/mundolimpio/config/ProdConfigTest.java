package com.mundolimpio.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.test.mock.mockito.MockBean;
import net.sourceforge.tess4j.ITesseract;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de configuracion del perfil prod para Cloud Run + Supabase.
 *
 * WHAT: Verifica que application-prod.yml expone correctamente las propiedades
 *       de Actuator, HikariCP y SSL requeridas para Cloud Run y Supabase.
 *
 * WHY: El perfil prod es la configuracion de produccion que usa Supabase
 *      PostgreSQL 16. Pool size=5, SSL=true y endpoints de health son
 *      CRITICOS para el funcionamiento correcto en Cloud Run. Si estas
 *      propiedades no cargan, la app no conecta a Supabase o Cloud Run
 *      no puede hacer health checks.
 *
 * HOW: Usa su propio PostgreSQLContainer (no hereda de AbstractIntegrationTest
 *      para evitar conflictos) con webEnvironment=NONE (no levanta Tomcat).
 *      El contexto Spring se carga con perfil prod y las propiedades se
 *      verifican via @Value y Environment.
 *      SSL se overrridea en @DynamicPropertySource porque el contenedor
 *      de test (postgres:16-alpine) no tiene SSL habilitado por defecto.
 *      La verificacion de SSL se hace leyendo el YAML directamente.
 *
 * DIFFERENCES con otros tests: Este test NO necesita servidor web porque
 *      solo verifica binding de propiedades. webEnvironment=NONE acelera
 *      el arranque (~2s vs ~8s con RANDOM_PORT). SSL se verifica via
 *      lectura directa del archivo YAML, no via @Value.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        // Valores minimos para que la URL del datasource en prod se resuelva
        // El @DynamicPropertySource sobreescribe spring.datasource.url con
        // la URL real del contenedor PostgreSQL.
        "PGHOST=localhost",
        "PGPORT=5432",
        "PGDATABASE=testdb",
        "PGUSER=test",
        "PGPASSWORD=test",
        // Deshabilitar schema-validation en test porque hay columnas
        // preexistentes (ej: sale_items.quantity NUMERIC vs INTEGER)
        // que no coinciden con las entidades JPA. Este es un problema
        // conocido del proyecto, no introducido por esta configuracion.
        // El test solo verifica propiedades de configuracion
        // (pool size, actuator, SSL), no el schema.
        "spring.jpa.hibernate.ddl-auto=none"
})
@Testcontainers
class ProdConfigTest {

    /**
     * Container PostgreSQL dedicado para este test de configuracion.
     * WHAT: Instancia de PostgreSQL 16 para satisfacer el datasource de prod
     * WHY: Perfil prod requiere una base de datos PostgreSQL real (no H2).
     *      Usamos testcontainers para proveer una instancia de PostgreSQL 16
     *      identica a la de produccion (misma imagen: postgres:16-alpine).
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withReuse(true);

    /**
     * WHAT: Sobreescribe la URL del datasource y DESHABILITA SSL para el test.
     * WHY: El contenedor postgres:16-alpine no tiene SSL configurado por defecto.
     *      application-prod.yml define ssl=true + sslmode=require, lo que romperia
     *      la conexion al test container. Overrideamos solo para la conexion;
     *      la verificacion de los valores YAML se hace leyendo el archivo directamente.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Deshabilitar SSL para el test container (postgres:16-alpine sin SSL)
        registry.add("spring.datasource.hikari.data-source-properties.ssl", () -> "false");
        registry.add("spring.datasource.hikari.data-source-properties.sslmode", () -> "disable");
    }

    @Value("${management.endpoints.web.exposure.include}")
    private String managementEndpoints;

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private int hikariMaxPoolSize;

    @Autowired
    private Environment environment;

    /**
     * WHAT: Mocks para dependencias externas del módulo receipt.
     * WHY: ProdConfigTest carga el perfil "prod" que incluye los beans
     *      del módulo receipt (TesseractOcrService, SupabaseStorageService).
     *      Sin estos mocks, el contexto falla porque no hay credenciales reales.
     */
    @MockBean
    private ITesseract tesseract;

    @MockBean
    private S3Client s3Client;

    /**
     * Lee el contenido de application-prod.yml para verificaciones directas.
     *
     * WHAT: Abre el archivo YAML del classpath como recurso de Spring
     * WHY: Necesitamos verificar los valores SSL del YAML sin pasar por
     *      el Spring Environment (que tiene los overrides de test).
     *      Leyendo el archivo directamente nos aseguramos que el YAML
     *      fuente tiene los valores esperados, sin interferencia de
     *      @DynamicPropertySource.
     */
    private String readProdYamlContent() {
        try {
            Resource resource = new DefaultResourceLoader()
                    .getResource("classpath:application-prod.yml");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer application-prod.yml", e);
        }
    }

    /**
     * WHAT: Verifica que los endpoints de Actuator expuestos en prod
     *       incluyen health e info para los health probes de Cloud Run.
     *
     * WHY: Cloud Run necesita /actuator/health para liveness y readiness.
     *      Sin health en prod, el contenedor nunca pasa los health checks
     *      y Cloud Run lo reinicia en loop infinito.
     *
     * GIVEN: application-prod.yml cargado con perfil prod activo
     * WHEN:  Se lee la propiedad management.endpoints.web.exposure.include
     * THEN:  Contiene "health" (requerido por Cloud Run)
     */
    @Test
    void prodProfile_ExposesHealthEndpoint() {
        assertThat(managementEndpoints)
                .as("El perfil prod debe exponer el endpoint /actuator/health "
                   + "para los health probes de Cloud Run")
                .contains("health");
    }

    /**
     * WHAT: Verifica que el pool de conexiones HikariCP esta limitado a 5
     *       en el perfil prod, cumpliendo con el free tier de Supabase.
     *
     * WHY: Supabase free tier limita a 15 conexiones simultaneas.
     *      Con max-instances=3 en Cloud Run, 3x5=15 conexiones totales.
     *      Si el pool fuera mas grande (ej: 10 por defecto), 3x10=30
     *      excederia el limite y las conexiones extra serian rechazadas.
     *
     * GIVEN: application-prod.yml con hikari.maximum-pool-size: 5
     * WHEN:  Se lee la propiedad spring.datasource.hikari.maximum-pool-size
     * THEN:  Es exactamente 5
     */
    @Test
    void prodProfile_LimitsPoolSizeToFive() {
        assertThat(hikariMaxPoolSize)
                .as("El perfil prod debe limitar el pool a 5 conexiones "
                   + "para no exceder el free tier de Supabase (15 conexiones)")
                .isEqualTo(5);
    }

    /**
     * WHAT: Verifica que SSL esta configurado en application-prod.yml
     *       leyendo el archivo YAML directamente.
     *
     * WHY: No podemos usar @Value para SSL porque @DynamicPropertySource
     *      sobreescribe esas propiedades (postgres:16-alpine sin SSL).
     *      Leyendo el YAML fuente verificamos que el archivo en si
     *      tiene la configuracion correcta, sin interferencia de overrides.
     *
     * GIVEN: application-prod.yml existe en el classpath
     * WHEN:  Se lee el contenido del archivo
     * THEN:  Contiene ssl: "true" y sslmode: require
     */
    @Test
    void prodProfile_RequiresSslConnection() {
        String yamlContent = readProdYamlContent();

        assertThat(yamlContent)
                .as("application-prod.yml debe contener ssl: \"true\" "
                   + "para conexion segura a Supabase")
                .contains("ssl: \"true\"");

        assertThat(yamlContent)
                .as("application-prod.yml debe contener sslmode: require "
                   + "para forzar SSL en la conexion a Supabase")
                .contains("sslmode: require");
    }

    /**
     * WHAT: Triangulacion — verifica que el perfil prod esta activo
     *       y que la propiedad de info endpoint tambien esta expuesta.
     *
     * WHY: Confirmamos que el perfil realmente es "prod" y que
     *      el endpoint /actuator/info tambien esta configurado
     *      (caso adicional para triangulacion del health endpoint).
     *
     * GIVEN: El contexto Spring cargado con perfil prod
     * WHEN:  Se verifica el perfil activo y endpoints de actuator
     * THEN:  El perfil es "prod" y "info" esta en los endpoints expuestos
     */
    @Test
    void prodProfile_IsActiveAndExposesInfoEndpoint() {
        // Verificar que el perfil prod esta realmente activo
        assertThat(environment.getActiveProfiles())
                .as("El perfil activo debe ser 'prod'")
                .contains("prod");

        // Triangulacion: verificar que info tambien esta expuesto
        assertThat(managementEndpoints)
                .as("El perfil prod tambien debe exponer /actuator/info "
                   + "para metadata de la aplicacion en Cloud Run")
                .contains("info");
    }

    /**
     * WHAT: Triangulacion adicional — verifica que el YAML de prod
     *       tambien define el pool size de 5 (doble verificacion).
     *
     * WHY: Confirmamos que tanto el Environment como el archivo YAML
     *      fuente tienen el mismo valor. Esto protege contra cambios
     *      accidentales en el YAML que no se reflejen en Environment.
     *
     * GIVEN: application-prod.yml con maximum-pool-size: 5
     * WHEN:  Se lee el YAML fuente
     * THEN:  Contiene la linea "maximum-pool-size: 5"
     */
    @Test
    void prodYaml_DefinesPoolSizeLimit() {
        String yamlContent = readProdYamlContent();

        assertThat(yamlContent)
                .as("application-prod.yml debe definir maximum-pool-size: 5 "
                   + "para respetar los limites del free tier de Supabase")
                .contains("maximum-pool-size: 5");
    }
}
