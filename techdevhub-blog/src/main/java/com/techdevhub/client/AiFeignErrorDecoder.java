package com.techdevhub.client;

import com.techdevhub.exception.AiCallException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ai-service Feign 错误解码器：HTTP 4xx/5xx + Result{code,message} 信封
 * → AiCallException(详细, httpStatus, aiCode, retryable)。
 *
 * 数字码 → retryable 映射（与 GlobalExceptionHandler.handleAiCallException 对偶）：
 *   1600 AI_SERVICE_TEMPORARY   → retryable=true （503）
 *   1602 AI_SERVICE_RATE_LIMITED→ retryable=true  （429）
 *   1601 AI_SERVICE_PERMANENT   → retryable=false （502）
 *   其他/无法解析                → 5xx/429 视为可重试，其余不可重试
 * 对端两段式错误码在 message 尾部以 "[XXX_YYY]" 形式附带，此处还原。
 */
public class AiFeignErrorDecoder implements ErrorDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern BRACKET_CODE = Pattern.compile("\\[([A-Z]+_[A-Z_]+)]\\s*$");

    static final int CODE_TEMPORARY = 1600;
    static final int CODE_PERMANENT = 1601;
    static final int CODE_RATE_LIMITED = 1602;

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        // body 只能消费一次，先读成 String 再解析
        String body = readBody(response);

        Integer numericCode = null;
        String message = null;
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node.hasNonNull("code")) {
                numericCode = node.get("code").asInt();
            }
            if (node.hasNonNull("message")) {
                message = node.get("message").asText();
            }
        } catch (Exception ignore) {
            // 非 JSON 响应（如网关错误页），走兜底映射
        }

        boolean retryable = switch (numericCode == null ? -1 : numericCode) {
            case CODE_TEMPORARY, CODE_RATE_LIMITED -> true;
            case CODE_PERMANENT -> false;
            default -> status >= 500 || status == 429;
        };

        String aiCode = null;
        if (message != null) {
            Matcher matcher = BRACKET_CODE.matcher(message);
            if (matcher.find()) {
                aiCode = matcher.group(1);
                // 去掉码后缀，还原人类可读文案
                message = message.substring(0, matcher.start()).trim();
            }
        }
        String detail = (message != null && !message.isBlank()) ? message : "AI service error " + status;
        return new AiCallException(detail, status, aiCode, retryable);
    }

    private String readBody(Response response) {
        // try-with-resources 里消费，避免 Feign 连接泄漏
        try (InputStream in = response.body().asInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
