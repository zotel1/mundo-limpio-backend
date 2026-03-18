package com.mundolimpio.application.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/*
*OpenAPI Configuration (Swagger)
*
* Define la informacion general de la API, seguridad, contactop, version, etc.
* Accedible en: http://localhost:8000/swagger-ui.html
*
* Tambien genera eel json de OpenAPI: http://localhost:8000/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    /*
    * Define la configuracion global de OpenAPI
    * Incluye informacion de lña API esquema de seguridad (JWT), contacto, etc.
    * */

    @Bean
    public OpenAPI customOpenAPI(){
        return new OpenAPI()
                // ====================== INFORMACIÓN GENERAL ======================
                .info(new Info()
                        .title("MundoLimpio API")
                        .version("1.0.0")
                        .description(
                                "Backend robusto para gestión de inventario y ventas de productos de limpieza.\n\n" +
                                        "Características principales:\n" +
                                        "- Trazabilidad financiera mediante estrategia FIFO (First-In, First-Out)\n" +
                                        "- Gestión de lotes de producción con costos específicos\n" +
                                        "- Cálculo de rentabilidad real basada en lotes vendidos\n" +
                                        "- Autenticación JWT (stateless)\n" +
                                        "- Operaciones ACID garantizadas con MySQL 8"
                        )
                        .contact(new Contact()
                                .name("MundoLimpio Team")
                                .email("support@mundolimpio.com")
                                .url("https://mundolimpio.com")
                        )
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")
                        )
                )
                // ====================== SEGURIDAD (JWT) ======================
                // Agregamos el esquema de seguridad JWT
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                // Definimos cómo el sistema debe interpretar el Bearer Token
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                        "JWT token obtenido en /api/v1/auth/login\n\n" +
                                                "Formato: Authorization: Bearer <token>\n\n" +
                                                "El token contiene:\n" +
                                                "- User ID\n" +
                                                "- Username\n" +
                                                "- Roles\n" +
                                                "- Expiración: 24 horas"
                                )
                        )
                );
    }
}
