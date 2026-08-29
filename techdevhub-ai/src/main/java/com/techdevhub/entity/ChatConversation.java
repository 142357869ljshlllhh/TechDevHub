package com.techdevhub.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话注册表实体（M8）：会话实体的属性挂这里（标题/归属/活跃时间），
 * 消息明细在 chat_transcript——与会话标题同理，blog_info 与 blog_moderation
 * 的关系：实体一张表，明细一张表。
 */
@Data
public class ChatConversation {

    private String conversationId;

    private Long userId;

    /** LLM 首轮生成的标题；NULL=未生成 */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
