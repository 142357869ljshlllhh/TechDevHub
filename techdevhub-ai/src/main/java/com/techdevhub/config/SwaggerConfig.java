package com.techdevhub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI aiOpenApi() {
        String schemeName = "BearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("TechDevHub AI API")
                        .version("v1.0.0")
                        .description("AI module APIs"))
                .components(new Components().addSecuritySchemes(
                        schemeName,
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
