package com.techdevhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "techdevhub.jwt")
public class JwtProperties {
    private String secretKey = "TechDevHubDefaultSecretKeyForJwtAuth2026";
    private Long expiration = 24 * 60 * 60 * 1000L;
    private String issuer = "techdevhub";
    private String headerName = "Authorization";
    private String tokenPrefix = "Bearer ";
    private List<String> excludePaths = new ArrayList<>(List.of(
            "/error",
            "/actuatot/**"
    ));
}
