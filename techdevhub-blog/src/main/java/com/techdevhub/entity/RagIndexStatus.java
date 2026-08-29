package com.techdevhub.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量索引状态（RAG 语料生命周期追踪）：ingest/移除的成败留痕，
 * 管理后台据此展示失败列表并支持重试/全量重建。
 */
@Data
public class RagIndexStatus {

    private Long blogId;

    /** pending / ok / failed / removed */
    private String status;

    private String errorCode;

    private LocalDateTime updateTime;

    /** join blog_info 的展示字段（非表列） */
    private String title;
}
