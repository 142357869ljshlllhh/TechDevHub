package com.techdevhub.jwt;

import com.techdevhub.annotation.IgnoreToken;
import com.techdevhub.config.JwtProperties;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
    private JWTUtil jwtUtil;
    private JwtProperties jwtProperties;

    public JwtInterceptor(JWTUtil jwtUtil, JwtProperties jwtProperties) {
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getBeanType().isAnnotationPresent(IgnoreToken.class)
                || handlerMethod.hasMethodAnnotation(IgnoreToken.class)) {
            return true;
        }

        String token = request.getHeader(jwtProperties.getHeaderName());
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long userId = jwtUtil.getUserId(token);
        request.setAttribute("currentUserId", userId);
        request.setAttribute("currentToken", token);
        return true;
    }
}