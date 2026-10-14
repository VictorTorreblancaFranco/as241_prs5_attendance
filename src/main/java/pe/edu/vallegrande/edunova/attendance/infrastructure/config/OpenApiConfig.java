package pe.edu.vallegrande.edunova.attendance.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Attendance Service API")
                        .version("1.0")
                        .description("Servicio de Asistencias - EduNova\n\n" +
                                "## Autenticación\n" +
                                "1. Obtener token vía POST /auth/login\n" +
                                "2. Usar el token en el botón Authorize\n" +
                                "3. Formato: Bearer {token}")
                        .contact(new Contact()
                                .name("Victor Torreblanca")
                                .email("victor.torreblanca@vallegrande.edu.pe"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://edunova.vg.com")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .schemaRequirement("bearer-jwt", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Ingrese el token JWT: Bearer {token}"));
    }
}
