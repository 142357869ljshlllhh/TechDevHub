package com.techdevhub.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "文章详情VO")
public class BlogDetailVO {
    private Long id;
    private Long userId;
    private String authorUsername;
    private String title;
    private String content;
    private Long categoryId;
    /** 0=审核中 1=已上架 2=驳回/下架 3=草稿；status!=1 仅作者本人可见 */
    private Integer status;
    private Integer likeCount;
    private Integer viewCount;
    private Integer commentCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
