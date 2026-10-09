package tech.tetengo.api.shared.infrastructure.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description served at {@code /v3/api-docs} and {@code /swagger-ui.html}. Every endpoint needs
 * a bearer JWT unless it is marked public; the version goes in the {@code Api-Version} header.
 */
@Configuration
public class OpenApiConfig {

    private static final String JWT = "jwt";

    @Bean
    OpenAPI documentacion() {
        return new OpenAPI()
                .info(new Info()
                        .title("Te Tengo API")
                        .version(ApiVersioning.V1)
                        .description("Backend API of Te Tengo: fall detection for older adults at home. Shared"
                                + " contract with the mobile app: docs/API_CONTRACT.md; household agent:"
                                + " docs/AGENT_CONTRACT.md. Send the header Api-Version: 1 (default 1). Errors are"
                                + " RFC 9457 problem details with a codigo property."))
                .components(new Components()
                        .addSecuritySchemes(
                                JWT,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(JWT));
    }
}
