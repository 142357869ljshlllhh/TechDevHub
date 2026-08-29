package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多轮对话请求 —— Python POST /api/v1/chat。
 * conversationId 由 Java 生成（8~64 字符，用 UUID 去横线即可），
 * 会话归属校验在 Python 侧做（X-User-Id 对不上返回 403）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @JsonProperty("conversation_id")
    @NotBlank(message = "conversationId 不能为空")
    @Size(min = 8, max = 64, message = "conversationId 长度须为 8~64")
    private String conversationId;

    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "消息超长（对端上限 4000 字）")
    private String message;
}
