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
    public OpenAPI blogOpenApi() {
        String schemeName = "BearerAuth";
        return new OpenAPI()
                .info(new Info().title("TechDevHub Blog API")
                        .version("v1.0.0")
                        .description("博客模块接口文档,支持分页查询文章、查看文章详情、发布文章、删除文章、修改文章"))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).
                                scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
