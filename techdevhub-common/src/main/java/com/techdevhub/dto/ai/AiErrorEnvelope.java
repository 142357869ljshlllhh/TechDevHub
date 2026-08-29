package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python AI 服务的统一失败信封：{"detail","code","retryable"}。
 * 所有 /api/v1/* 端点失败时都是这一个形状（4xx/5xx），因此只需一种反序列化模型全端点复用。
 * code 为两段式 {MODULE}_{NATURE}（如 MODERATION_LLM_TEMPORARY），
 * retryable 决定 Java 侧重试状态机走"退避重试"还是"直接 GIVEUP"。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiErrorEnvelope {

    /** 技术细节或人类安全文案（对端对人类端点已做脱敏，可直接透传前端） */
    private String detail;

    /** 两段式错误码，如 RAG_TEMPORARY / AGENT_PERMANENT */
    private String code;

    @JsonProperty("retryable")
    private Boolean retryable;

    /** 对端信封缺 retryable 字段时按不可重试处理——保守策略，宁可不重试也不放大故障 */
    public boolean isRetryableSafe() {
        return Boolean.TRUE.equals(retryable);
    }
}
