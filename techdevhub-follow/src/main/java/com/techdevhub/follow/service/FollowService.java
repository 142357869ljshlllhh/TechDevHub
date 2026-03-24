package com.techdevhub.follow.service;

public interface FollowService {

    void follow(Long userId, Long followUserId);

    void unfollow(Long userId, Long followUserId);
}
