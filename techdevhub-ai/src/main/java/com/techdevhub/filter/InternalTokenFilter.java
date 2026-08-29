package com.techdevhub.filter;

import com.techdevhub.config.AiPythonProperties;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /ai/internal/** 门禁过滤器：校验 X-Internal-Token（服务间调用，无用户 JWT）。
 *
 * 为什么不用 JWT：审核在 blog 侧异步线程执行，没有用户请求上下文可透传 Authorization；
 * 服务间身份用共享 token，与 Python 侧门禁同一模型，运维心智一致。
 * token 留空 = 门禁关闭，仅限本地联调（与 Python 侧同规则）。
 */
@Slf4j
public class InternalTokenFilter extends OncePerRequestFilter {

    private final AiPythonProperties props;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(AiPythonProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        // compose 侧用 ${AI_INTERNAL_TOKEN:?err} 强制必填，这里是代码侧的第二道哨兵：
        // 万一部署层漏配，启动日志必须有显眼的 ERROR，而不是静默开门
        if (!StringUtils.hasText(props.getInternalToken())) {
            log.error("InternalTokenFilter 门禁已关闭（internal-token 为空）——仅供本地联调，生产环境禁止此状态！");
        }
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String expected = props.getInternalToken();
        if (!StringUtils.hasText(expected)) {
            chain.doFilter(request, response); // 本地联调模式
            return;
        }
        String provided = request.getHeader("X-Internal-Token");
        if (!StringUtils.hasText(provided)) {
            writeError(request, response, HttpStatus.UNAUTHORIZED, "缺少 X-Internal-Token");
            return;
        }
        // 常量时间比较，防时序侧信道
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            writeError(request, response, HttpStatus.FORBIDDEN, "X-Internal-Token 不匹配");
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                new Result(ErrorCode.AI_INTERNAL_UNAUTHORIZED.getCode(), detail, null)));
        log.warn("内部端点门禁拒绝: {} {} ({})", request.getMethod(), request.getRequestURI(), detail);
    }
}
