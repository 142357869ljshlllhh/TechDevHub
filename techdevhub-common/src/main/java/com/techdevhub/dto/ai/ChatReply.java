package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 多轮对话响应 —— Python 成功响应直接是本对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatReply {

    private String reply;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("tokens_used")
    private Long tokensUsed;
}
