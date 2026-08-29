package com.techdevhub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdevhub.filter.InternalTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * /ai/internal/** 门禁过滤器注册。
 * 只拦截内部转发端点，前端代理端点继续走 JWT 拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class AiInternalSecurityConfig {

    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(
            AiPythonProperties props, ObjectMapper objectMapper) {
        FilterRegistrationBean<InternalTokenFilter> registration =
                new FilterRegistrationBean<>(new InternalTokenFilter(props, objectMapper));
        registration.addUrlPatterns("/ai/internal/*");
        // 越早越好：门禁放最前，未授权请求不进任何业务链路
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
