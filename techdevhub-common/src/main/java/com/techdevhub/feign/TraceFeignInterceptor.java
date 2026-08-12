package com.techdevhub.feign;

import com.techdevhub.context.UserContext;
import com.techdevhub.interceptor.TraceInterceptor;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求拦截器：让服务间调用也带上链路与鉴权信息。
 *
 * <ul>
 *   <li>透传 {@value TraceInterceptor#TRACE_ID_HEADER}：优先取 {@link UserContext} 中的 traceId，
 *       其次取 {@link MDC}（兜底），使一次用户请求跨多个微服务时共享同一个 traceId；</li>
 *   <li>透传 {@code Authorization}：从当前 Servlet 请求中取（{@link RequestContextHolder}），
 *       修复此前服务间 Feign 调用不携带令牌、下游拦截器可能拒绝（405）的问题。</li>
 * </ul>
 *
 * <p>Spring Cloud OpenFeign 会自动把容器中所有 {@link RequestInterceptor} bean 应用到全部 FeignClient。
 * 若当前不在 HTTP 请求上下文（如启动期 / 定时任务），则跳过 Authorization 透传，避免 NPE。
 */
public class TraceFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = UserContext.getTraceId();
        if (traceId == null) {
            traceId = MDC.get("traceId");
        }
        if (traceId != null && !traceId.isBlank()) {
            template.header(TraceInterceptor.TRACE_ID_HEADER, traceId);
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authorization = request.getHeader("Authorization");
            if (authorization != null && !authorization.isBlank()) {
                template.header("Authorization", authorization);
            }
        }
    }
}
