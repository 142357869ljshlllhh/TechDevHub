package com.techdevhub.follow.service;

import com.techdevhub.follow.vo.FollowersVO;

import java.util.List;

public interface FollowService {

    void follow(Long userId, Long followUserId);

    void unfollow(Long userId, Long followUserId);

    List<FollowersVO> getFollowers(Long userId);
}
