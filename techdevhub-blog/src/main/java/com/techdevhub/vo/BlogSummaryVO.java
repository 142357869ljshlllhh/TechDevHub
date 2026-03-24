package com.techdevhub.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogSummaryVO {
    private Long id;
    private Long userId;
    private String authorUsername;
    private String title;
    private String contentPreview;
    private Integer categoryId;
    private Integer likeCount;
    private Integer viewCount;
    private Integer commentCount;
    private LocalDateTime createTime;
}
