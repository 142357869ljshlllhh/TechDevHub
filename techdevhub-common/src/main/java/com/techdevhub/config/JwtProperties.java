package com.techdevhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "techdevhub.jwt")
public class JwtProperties {
    private String secretKey;  // 不设默认值（fail-closed）：必须由 TECHDEVHUB_JWT_SECRETKEY 注入，缺失即启动失败，杜绝可伪造的硬编码兜底密钥
    private Long expiration = 24 * 60 * 60 * 1000L;
    private String issuer = "techdevhub";
    private String headerName = "Authorization";
    private String tokenPrefix = "Bearer ";
    private List<String> excludePaths = new ArrayList<>(List.of(
            "/users/register",
            "/users/login",
            "/error",
            "/actuator/**"
    ));
}
