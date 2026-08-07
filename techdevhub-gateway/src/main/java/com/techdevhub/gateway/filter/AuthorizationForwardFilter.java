package com.techdevhub.gateway.filter;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 强制把客户端请求中的 Authorization 头透传给下游微服务。
 *
 * 问题背景：category 等需要登录态的接口，网关把请求路由到下游后，
 * 下游 JWT 拦截器拿不到 token，于是在 preHandle 抛 unauthorized，
 * 又因为拦截器异常绕过了 @RestControllerAdvice，被当作 HTTP 500 返回。
 *
 * 该过滤器无论网关的默认头转发行为如何（以及是否被 Nacos 下发的过滤规则覆盖），
 * 都保证 Authorization 头随请求一起下发到下游，使拦截器能正确解析当前用户。
 */
@Component
public class AuthorizationForwardFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            // 先删除再写入，避免产生重复的 Authorization 头
            var mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 尽早执行，确保在路由/负载均衡等过滤器之前把头补上
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
