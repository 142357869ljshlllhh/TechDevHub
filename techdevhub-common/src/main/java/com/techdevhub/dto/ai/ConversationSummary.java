package com.techdevhub.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 会话列表条目 —— 每个会话的最后一条消息预览（前端侧边栏）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {

    private String conversationId;

    /** 最后一条消息的角色（user/assistant/event）——前端可据此选图标 */
    private String lastRole;

    private String lastMessage;

    /** epoch millis */
    private Long lastTime;
}
