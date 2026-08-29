package com.techdevhub.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 管理端一键重审请求：指定 blogId 列表（≤50 篇，对端硬上限）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationRecheckDTO {

    @NotEmpty(message = "blogIds 不能为空")
    @Size(max = 50, message = "单批最多 50 篇（对端硬上限）")
    private List<Long> blogIds;
}
