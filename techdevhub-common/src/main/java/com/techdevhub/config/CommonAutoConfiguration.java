package com.techdevhub.config;

import com.techdevhub.exception.GlobalExceptionHandler;
import com.techdevhub.feign.TraceFeignInterceptor;
import com.techdevhub.interceptor.TraceInterceptor;
import com.techdevhub.jwt.JWTUtil;
import com.techdevhub.jwt.JwtInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
@EnableConfigurationProperties({JwtProperties.class,ServiceEndpointProperties.class})
public class CommonAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return PasswordEncodeConfig.bcryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public JWTUtil jwtUtil(JwtProperties jwtProperties) {
        return new JWTUtil(jwtProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtInterceptor jwtAuthenticationInterceptor(JWTUtil jwtUtil, JwtProperties jwtProperties) {
        return new JwtInterceptor(jwtUtil, jwtProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceInterceptor traceInterceptor() {
        return new TraceInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebMvcConfig webMvcConfig(TraceInterceptor traceInterceptor, JwtInterceptor jwtInterceptor, JwtProperties jwtProperties) {
        return new WebMvcConfig(traceInterceptor, jwtInterceptor, jwtProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceFeignInterceptor traceFeignInterceptor() {
        return new TraceFeignInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
