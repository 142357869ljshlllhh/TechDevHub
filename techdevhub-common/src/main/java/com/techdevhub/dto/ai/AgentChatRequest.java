package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 社区助手请求 —— Python POST /api/v1/agent/chat。
 * 两跳协议：第一跳 message 正常传、confirmAction=null；
 * 第二跳 message 传空串、confirmAction 原样回传上一跳的 pending_action。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequest {

    @JsonProperty("conversation_id")
    @NotBlank(message = "conversationId 不能为空")
    @Size(min = 8, max = 64, message = "conversationId 长度须为 8~64")
    private String conversationId;

    @Size(max = 4000, message = "消息超长（对端上限 4000 字）")
    private String message;

    @JsonProperty("confirm_action")
    private AgentConfirmAction confirmAction;
}
