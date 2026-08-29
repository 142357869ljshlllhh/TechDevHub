package com.techdevhub.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 问答响应。
 * 关键语义：rejected=true = 正常业务结果（知识库未覆盖，HTTP 仍是 200）——
 * 绝不重试，直接把 answer 原文展示给前端。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QAResponse {

    /** 带 [blog_id] 引用的回答 */
    private String answer;

    private List<RagSource> sources;

    private Boolean rejected;
}
