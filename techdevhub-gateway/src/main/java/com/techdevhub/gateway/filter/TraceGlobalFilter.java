package com.techdevhub.gateway.filter;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全链路追踪过滤器（网关入口）。
 *
 * <p>作为整条调用链的最前端，负责：
 * <ul>
 *   <li>若请求已携带 {@value #TRACE_ID_HEADER}（例如前端 / 上游网关透传），则复用；</li>
 *   <li>否则生成新的 traceId（UUID 去横线），写入发往下游的请求头。</li>
 * </ul>
 * 下游各微服务会在自己的 TraceInterceptor 中复用该 traceId，从而一次用户请求在网关 + 多个微服务间共享同一个 id。
 */
@Component
public class TraceGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        ServerWebExchange mutated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(TRACE_ID_HEADER, traceId)
                        .build())
                .build();
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        // 尽早执行，在路由 / 负载均衡之前把 traceId 补到下游请求头
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
