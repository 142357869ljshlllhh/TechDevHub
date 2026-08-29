package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核请求 —— 与 Python POST /api/v1/moderation/check 的请求体逐字段对齐。
 * 为什么逐字段 @JsonProperty 而不用全局 PropertyNamingStrategy：
 * common 模块同时承载 Java 风格与 Python 风格 DTO，全局策略会互相污染；
 * 显式声明最不容易在对端契约变更时漂移。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationCheckRequest {

    @JsonProperty("blog_id")
    @NotNull(message = "blogId 不能为空")
    private Long blogId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题超长（对端上限 200 字）")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 50000, message = "正文超长（对端上限 50000 字）")
    private String content;

    @JsonProperty("author_id")
    @NotNull(message = "authorId 不能为空")
    private Long authorId;
}
