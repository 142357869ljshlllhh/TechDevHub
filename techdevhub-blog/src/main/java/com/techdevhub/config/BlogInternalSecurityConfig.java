package com.techdevhub.config;

import com.techdevhub.filter.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * /api/v1/internal/**（Python 工具回调）门禁过滤器注册。
 * JWT 拦截器已通过 exclude-paths 放行该前缀，由本过滤器接管校验。
 */
@Configuration
public class BlogInternalSecurityConfig {

    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(
            @Value("${techdevhub.internal-token:}") String internalToken) {
        FilterRegistrationBean<InternalTokenFilter> registration =
                new FilterRegistrationBean<>(new InternalTokenFilter(internalToken));
        registration.addUrlPatterns("/api/v1/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
