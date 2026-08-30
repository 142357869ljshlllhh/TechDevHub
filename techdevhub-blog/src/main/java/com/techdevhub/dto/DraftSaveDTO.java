package com.techdevhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "草稿保存DTO")
public class DraftSaveDTO {
    @Schema(description = "标题（草稿可为空，发布时必填）")
    private String title;
    @Schema(description = "内容")
    private String content;
    @Schema(description = "类别")
    private Long categoryId;
}
