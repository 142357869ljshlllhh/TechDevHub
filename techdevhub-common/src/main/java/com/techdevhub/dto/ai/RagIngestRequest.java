package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 摄取请求 —— Python POST /api/v1/rag/ingest。
 * content 传正文原文即可（HTML 由 Python 服务端清洗），标题会拼进 chunk 头。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagIngestRequest {

    @JsonProperty("blog_id")
    @NotNull(message = "blogId 不能为空")
    private Long blogId;

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;
}
