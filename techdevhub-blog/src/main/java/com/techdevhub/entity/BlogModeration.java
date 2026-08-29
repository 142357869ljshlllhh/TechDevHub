package com.techdevhub.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审核留痕实体（blog_moderation 表）。
 * verdict 为空 = 审核服务故障 GIVEUP，看 error_code / review_reason。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogModeration {

    private Long id;

    private Long blogId;

    /** approve | review | reject | null(GIVEUP) */
    private String verdict;

    private Double confidence;

    private String reason;

    /** rule | llm */
    private String layer;

    /** 对端两段式错误码，如 MODERATION_LLM_TEMPORARY */
    private String errorCode;

    /** AGENT_FAILURE = 审核服务故障挂起（管理端一键重审分组依据） */
    private String reviewReason;

    private Long latencyMs;

    private LocalDateTime createTime = LocalDateTime.now();
}
