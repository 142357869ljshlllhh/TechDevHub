package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 社区助手确认动作 —— Agent 写工具（如 create_draft）的两跳确认载体。
 * args 原样回传 Python 侧给出的 pending_action.args；
 * ⚠️ 不要在 args 里放 user_id：Python 侧用 X-User-Id 强制覆盖，传了也会被忽略。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfirmAction {

    /** 目前仅支持 create_draft（写工具白名单在 Python 侧） */
    @NotBlank(message = "tool 不能为空")
    private String tool;

    /** 待执行参数，原样回传，不做本地解释 */
    private Map<String, Object> args;
}
