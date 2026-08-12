package com.techdevhub.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdevhub.annotation.IgnoreToken;
import com.techdevhub.config.JwtProperties;
import com.techdevhub.context.UserContext;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
    private JWTUtil jwtUtil;
    private JwtProperties jwtProperties;

    public JwtInterceptor(JWTUtil jwtUtil, JwtProperties jwtProperties) {
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getBeanType().isAnnotationPresent(IgnoreToken.class)
                || handlerMethod.hasMethodAnnotation(IgnoreToken.class)) {
            return true;
        }

        String token = request.getHeader(jwtProperties.getHeaderName());
        if (!StringUtils.hasText(token)) {
            // 不在拦截器里抛异常：@RestControllerAdvice 捕获不到 preHandle 的异常，
            // 会被 servlet 容器当作 HTTP 500；这里直接写出统一的 Result{code:405}。
            writeError(response, ErrorCode.UNAUTHORIZED);
            return false;
        }

        try {
            Long userId = jwtUtil.getUserId(token);
            Boolean isAdmin = jwtUtil.getIsAdmin(token);
            request.setAttribute("currentUserId", userId);
            request.setAttribute("currentToken", token);
            // 填充请求级上下文（ThreadLocal），供 Service 层直接读取，无需层层传参
            UserContext.setUserId(userId);
            UserContext.setIsAdmin(Boolean.TRUE.equals(isAdmin));
            return true;
        } catch (BusinessException e) {
            // token 过期/无效同样返回统一 Result，避免 500
            writeError(response, e.getErrorCode());
            return false;
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) {
        try {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(new ObjectMapper().writeValueAsString(Result.fail(errorCode)));
            response.getWriter().flush();
        } catch (IOException ex) {
            // 兜底：绝不在拦截器里抛异常，避免被 servlet 容器当作 HTTP 500
            response.setStatus(HttpStatus.OK.value());
        }
    }
}