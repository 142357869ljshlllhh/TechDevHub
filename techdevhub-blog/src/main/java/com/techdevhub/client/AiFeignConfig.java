package com.techdevhub.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * ai-service 专用 Feign 配置（仅由 @FeignClient(configuration=...) 引用）。
 *
 * ⚠️ 故意不加 @Configuration：Feign 的 configuration 类若被组件扫描收录，
 * 其 Bean 会应用到全部 FeignClient（包括 user-service 等），ErrorDecoder 会串味。
 *
 * 两件事：
 * 1. 注入 X-Internal-Token——ai-service 的 /ai/internal/** 门禁；
 * 2. 注册错误解码器，把 AI_* 数字码信封还原成 AiCallException（retryable 不丢）。
 */
public class AiFeignConfig {

    @Bean
    public ErrorDecoder aiErrorDecoder() {
        return new AiFeignErrorDecoder();
    }

    @Bean
    public RequestInterceptor aiInternalTokenInterceptor(
            @Value("${techdevhub.internal-token:}") String internalToken) {
        return template -> {
            if (StringUtils.hasText(internalToken)) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }
}
