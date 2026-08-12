package com.techdevhub.config;

import com.techdevhub.interceptor.TraceInterceptor;
import com.techdevhub.jwt.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TraceInterceptor traceInterceptor;
    private final JwtInterceptor jwtAuthenticationInterceptor;
    private final JwtProperties jwtProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 追踪拦截器排在最前：所有请求（含公开接口）都生成/复用 traceId，并在最后统一清理
        registry.addInterceptor(traceInterceptor)
                .addPathPatterns("/**");
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(jwtProperties.getExcludePaths());
    }
}
