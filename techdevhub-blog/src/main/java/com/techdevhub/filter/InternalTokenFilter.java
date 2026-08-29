package com.techdevhub.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /api/v1/internal/**（Python AI 工具回调）门禁过滤器。
 *
 * 为什么不用 JWT：调用方是 Python 服务（服务间调用），不是登录用户浏览器；
 * 与 ai-service 的 InternalTokenFilter、Python 侧门禁同一模型，运维心智一致。
 * token 留空 = 门禁关闭，仅限本地联调（与 Python 侧同规则）。
 */
@Slf4j
@RequiredArgsConstructor
public class InternalTokenFilter extends OncePerRequestFilter {

    private final String internalToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!StringUtils.hasText(internalToken)) {
            chain.doFilter(request, response); // 本地联调模式
            return;
        }
        String provided = request.getHeader("X-Internal-Token");
        if (!StringUtils.hasText(provided)) {
            reject(request, response, HttpStatus.UNAUTHORIZED, "missing X-Internal-Token");
            return;
        }
        // 常量时间比较，防时序侧信道
        if (!MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            reject(request, response, HttpStatus.FORBIDDEN, "X-Internal-Token mismatch");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 裸 JSON 错误体——Python raise_for_status 后只看状态码，body 供日志排查 */
    private void reject(HttpServletRequest request, HttpServletResponse response,
                        HttpStatus status, String detail) throws IOException {
        log.warn("内部回调端点门禁拒绝: {} {} ({})",
                request.getMethod(), request.getRequestURI(), detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"detail\": \"" + detail + "\"}");
    }
}
