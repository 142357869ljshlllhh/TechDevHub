package com.techdevhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "techdevhub.service")
public class ServiceEndpointProperties {

    private String userUrl = "http://localhost:8081";
    private String categoryUrl = "http://localhost:8082";
    private String blogUrl = "http://localhost:8083";
    private String likeUrl = "http://localhost:8084";
    private String followUrl = "http://localhost:8085";
    private String commentUrl = "http://localhost:8086";
    private String adminUrl = "http://localhost:8087";
    private String gatewayUrl = "http://localhost:8090";
}

