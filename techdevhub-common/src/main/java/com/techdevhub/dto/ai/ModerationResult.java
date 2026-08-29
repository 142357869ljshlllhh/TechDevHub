package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 审核结果 —— Python POST /api/v1/moderation/check 的成功响应（无包装，直接是本对象）。
 * verdict: approve | review | reject
 * layer:   rule（规则引擎直判）| llm（LLM 判定）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationResult {

    private String verdict;

    private Double confidence;

    private List<ModerationViolation> violations;

    /** 一句话理由，review/reject 时展示给用户或管理员 */
    private String reason;

    private String layer;

    @JsonProperty("latency_ms")
    private Long latencyMs;
}
