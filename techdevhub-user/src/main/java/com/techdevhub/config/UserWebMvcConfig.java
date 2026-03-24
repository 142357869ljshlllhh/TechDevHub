package com.techdevhub.config;

import com.techdevhub.config.JwtProperties;
import com.techdevhub.interceptor.UserLogoutInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class UserWebMvcConfig implements WebMvcConfigurer {

    private final UserLogoutInterceptor userLogoutInterceptor;
    private final JwtProperties jwtProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userLogoutInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(jwtProperties.getExcludePaths());
    }
}
