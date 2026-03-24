package com.techdevhub.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "分页查询DTO")
public class BlogPageSelectDTO {

    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private Integer categoryId;
    private String keyword;
    private Long userId;
}