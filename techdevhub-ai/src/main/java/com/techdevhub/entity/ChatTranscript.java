package com.techdevhub.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话记录（用户可见的完整对话，M7）。
 *
 * 与 Redis 的 LLM 工作记忆（chat:history:{cid}）是两个存储：工作记忆服务于
 * 上下文拼装（滑动窗口+摘要压缩+7天TTL，允许有损），本表面向用户回放
 * （每轮完整、追加有序、持久化）。写入方是 Python Agent 轮末回调
 * （POST /ai/internal/chat/transcript），读取方是 ai-service 的 history/conversations 端点。
 */
@Data
public class ChatTranscript {

    private Long id;

    private String conversationId;

    private Long userId;

    /** user / assistant / event */
    private String role;

    private String content;

    private LocalDateTime createTime;
}
