package com.techdevhub.like.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点赞操作结果：当前用户是否已点赞 + 文章最新点赞总数。
 * 点赞总数直接由 blog_like_info 表实时统计（权威数据源），
 * 避免前端乐观计数与后端幂等计数“双重记账”导致按钮数字与 DB 永久不一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeResult {
    /** 操作后当前用户是否点赞 */
    private Boolean liked;
    /** 操作后文章最新点赞总数（基于 blog_like_info 表 is_delete=0 的实时 COUNT） */
    private Long likeCount;
}
