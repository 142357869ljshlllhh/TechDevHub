package com.techdevhub.like.service;

public interface BlogLikeService {

    void like(Long userId, Long blogId);

    void unlike(Long userId, Long blogId);
}
