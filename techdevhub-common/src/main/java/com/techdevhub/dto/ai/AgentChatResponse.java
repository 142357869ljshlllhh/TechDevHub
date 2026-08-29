package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 社区助手响应 —— pendingAction 非空 = 需要用户确认的第二跳；null = 普通回复。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {

    private String reply;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("pending_action")
    private AgentPendingAction pendingAction;
}
