package com.techdevhub.interceptor;

import com.techdevhub.config.JwtProperties;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserLogoutInterceptor implements HandlerInterceptor {

    private static final String LOGOUT_TOKEN_PREFIX = "user:logout:token:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestPath = request.getRequestURI();
        List<String> excludePaths = jwtProperties.getExcludePaths();
        if (excludePaths != null && excludePaths.stream().anyMatch(path -> antPathMatcher.match(path, requestPath))) {
            return true;
        }

        String token = request.getHeader(jwtProperties.getHeaderName());
        if (!StringUtils.hasText(token)) {
            return true;
        }

        Boolean exists = stringRedisTemplate.hasKey(LOGOUT_TOKEN_PREFIX + token);
        if (Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.TOKEN_LOGGED_OUT);
        }
        return true;
    }
}
