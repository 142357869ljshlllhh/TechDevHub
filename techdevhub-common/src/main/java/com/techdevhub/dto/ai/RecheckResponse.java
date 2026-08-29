package com.techdevhub.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 批量重审响应 —— results 与请求 items 顺序一一对应；对端整批 fail-fast，不会出现部分成功。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecheckResponse {

    private List<ModerationResult> results;
}
