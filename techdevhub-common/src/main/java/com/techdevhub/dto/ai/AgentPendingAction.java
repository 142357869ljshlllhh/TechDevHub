package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 待确认动作 —— 第一跳响应中 pending_action 非空时，前端展示 summary 并请求用户确认。
 * 生命周期只到确认/取消为止，不做本地持久化（对端会话记忆里已有上下文）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentPendingAction {

    private String tool;

    private Map<String, Object> args;

    /** 人类可读的提议说明，如"创建草稿《xx》" */
    private String summary;
}
