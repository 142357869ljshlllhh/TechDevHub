package com.techdevhub.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审核命中明细 —— 对应 Python violations[] 元素，字段全小写单词，无需映射。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationViolation {

    /** ad | spam | politics | porn | abuse | privacy | other */
    private String category;

    /** 命中片段（≤200 字） */
    private String snippet;

    private Double confidence;
}
