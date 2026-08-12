package com.techdevhub.interceptor;

import com.techdevhub.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 全链路追踪拦截器：在请求最入口处解析/生成 traceId。
 *
 * <ul>
 *   <li>若上游（网关 / 前端 / 上游服务 Feign）已带上 {@value #TRACE_ID_HEADER}，则复用，保证整条调用链同一个 id；</li>
 *   <li>否则生成本次请求的 traceId（UUID 去横线）。</li>
 * </ul>
 *
 * 解析后写入 {@link UserContext} 与 SLF4J {@link MDC}（日志 pattern 含 %X{traceId} 即可在每行日志看到），
 * 并通过响应头回传，方便前端 / 调用方定位问题。请求结束在 afterCompletion 中清理。
 *
 * <p>注册顺序应排在所有拦截器最前面（WebMvcConfig 中第一个 addInterceptor），
 * 这样即便 @IgnoreToken 的公开接口也能被追踪，且 afterCompletion 在最后执行、统一清理。
 */
public class TraceInterceptor implements HandlerInterceptor {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        UserContext.setTraceId(traceId);
        MDC.put("traceId", traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        MDC.remove("traceId");
        // 清理整个 UserContext（含 userId/isAdmin，由 JwtInterceptor 在其 preHandle 中写入）
        UserContext.clear();
    }
}
