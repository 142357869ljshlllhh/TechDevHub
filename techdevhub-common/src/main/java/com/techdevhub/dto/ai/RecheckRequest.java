package com.techdevhub.dto.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 批量重审请求 —— 对端上限 50 篇/批，超限返回 422，这里前置校验少打一次网络。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecheckRequest {

    @Valid
    @NotEmpty(message = "重审列表不能为空")
    @Size(max = 50, message = "单批最多 50 篇（对端硬上限）")
    private List<ModerationCheckRequest> items;
}
