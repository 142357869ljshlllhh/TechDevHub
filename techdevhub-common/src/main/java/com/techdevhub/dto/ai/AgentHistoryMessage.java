package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 会话历史单条消息 —— Python GET /api/v1/agent/history 的 messages 元素。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentHistoryMessage {

    private String role;

    private String content;

    private Long ts;
}
