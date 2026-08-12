package com.techdevhub.like.service;

import com.techdevhub.like.dto.LikeResult;

public interface BlogLikeService {

    LikeResult like(Long userId, Long blogId);

    LikeResult unlike(Long userId, Long blogId);

    // 查询当前用户对单篇博客的点赞态（用于前端按用户隔离展示）
    boolean isLiked(Long userId, Long blogId);

    // 批量查询当前用户对多篇博客的点赞态，返回 blogId -> 是否已赞
    java.util.Map<Long, Boolean> batchIsLiked(Long userId, java.util.List<Long> blogIds);
}
