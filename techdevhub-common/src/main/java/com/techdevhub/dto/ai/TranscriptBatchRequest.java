package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话记录批量写入请求 —— Python Agent 轮末回调 POST /ai/internal/chat/transcript。
 *
 * user_id 由 Python 从其请求上下文（X-User-Id）透传，Java 端不再二次校验归属
 * （调用方是可信的内部服务，用户身份在校话链路已验过）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptBatchRequest {

    @JsonProperty("conversation_id")
    @NotBlank(message = "conversationId 不能为空")
    private String conversationId;

    @JsonProperty("user_id")
    private Long userId;

    @Valid
    @NotEmpty(message = "entries 不能为空")
    private List<Entry> entries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {

        /** user / assistant / event */
        @NotBlank(message = "role 不能为空")
        private String role;

        @NotBlank(message = "content 不能为空")
        private String content;
    }
}
