package com.techdevhub.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文章类")
public class BlogInfo {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private Integer categoryId;
    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
    private Integer likeCount = 0;
    private Integer viewCount = 0;
    private Integer commentCount = 0;
    private Integer isDelete = 0;
    private Integer status = 0;
}
