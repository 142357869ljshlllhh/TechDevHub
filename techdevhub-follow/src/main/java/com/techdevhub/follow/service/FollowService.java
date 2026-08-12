package com.techdevhub.follow.service;

import com.techdevhub.follow.vo.FollowCountsVO;
import com.techdevhub.follow.vo.FollowersVO;

import java.util.List;

public interface FollowService {

    void follow(Long userId, Long followUserId);

    void unfollow(Long userId, Long followUserId);

    List<FollowersVO> getFollowers(Long userId);

    // 我关注的用户列表
    List<FollowersVO> getFollowing(Long userId);

    // 当前用户是否关注了 targetUserId
    boolean isFollowing(Long userId, Long targetUserId);

    // 关注数 / 粉丝数
    FollowCountsVO getCounts(Long userId);
}
