package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 会话历史回放 —— 前端刷新后恢复消息列表（system 摘要帧 Python 侧已过滤）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentHistoryResponse {

    @JsonProperty("conversation_id")
    private String conversationId;

    private List<AgentHistoryMessage> messages;
}
