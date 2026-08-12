package com.techdevhub.follow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.follow.client.UserClient;
import com.techdevhub.follow.entity.FollowInfo;
import com.techdevhub.follow.mapper.FollowMapper;
import com.techdevhub.follow.service.FollowService;
import com.techdevhub.follow.vo.FollowCountsVO;
import com.techdevhub.follow.vo.FollowersVO;
import com.techdevhub.result.Result;
import com.techdevhub.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private static final String FOLLOWING_KEY = "follow:user:";
    private static final String FOLLOWERS_KEY = "follow:fans:";
    private final ObjectMapper objectMapper;
    private final FollowMapper followMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserClient userClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long userId, Long followUserId) {
        if (userId.equals(followUserId)) {
            throw new BusinessException(ErrorCode.FOLLOW_SELF_NOT_ALLOWED);
        }
        FollowInfo relation = followMapper.selectRelation(userId, followUserId);
        if (relation == null) {
            FollowInfo followInfo = new FollowInfo();
            followInfo.setId(snowflakeIdGenerator.nextId());
            followInfo.setUserId(userId);
            followInfo.setFollowUserId(followUserId);
            followInfo.setIsDelete(0);
            if (followMapper.insert(followInfo) == 0) {
                throw new BusinessException(ErrorCode.FOLLOW_CREATE_FAILED);
            }
        } else if (relation.getIsDelete() != null && relation.getIsDelete() == 1) {
            if (followMapper.updateDeleteStatus(relation.getId(), 0) == 0) {
                throw new BusinessException(ErrorCode.FOLLOW_CREATE_FAILED);
            }
        }
        stringRedisTemplate.opsForSet().add(FOLLOWING_KEY + userId, String.valueOf(followUserId));
        stringRedisTemplate.opsForSet().add(FOLLOWERS_KEY + followUserId, String.valueOf(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long userId, Long followUserId) {
        FollowInfo relation = followMapper.selectRelation(userId, followUserId);
        if (relation == null || relation.getIsDelete() == null || relation.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.FOLLOW_RELATION_NOT_FOUND);
        }
        if (followMapper.updateDeleteStatus(relation.getId(), 1) == 0) {
            throw new BusinessException(ErrorCode.FOLLOW_CANCEL_FAILED);
        }
        stringRedisTemplate.opsForSet().remove(FOLLOWING_KEY + userId, String.valueOf(followUserId));
        stringRedisTemplate.opsForSet().remove(FOLLOWERS_KEY + followUserId, String.valueOf(userId));
    }

    public List<FollowersVO> getFollowers(Long userId){
        List<Long> list = followMapper.getFollowers(userId);
        List<FollowersVO> list1 = new ArrayList<>();
        for (Long id:list){
            Result result = userClient.getProfile(id);
            if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
                throw new BusinessException(ErrorCode.FOLLOW_USER_CLIENT_FAIL);
            }
            FollowersVO vo = objectMapper.convertValue(result.getData(), FollowersVO.class);
            list1.add(vo);
        }
        return list1;
    }

    @Override
    public List<FollowersVO> getFollowing(Long userId) {
        List<Long> list = followMapper.getFollowing(userId);
        List<FollowersVO> list1 = new ArrayList<>();
        for (Long id : list) {
            Result result = userClient.getProfile(id);
            if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
                throw new BusinessException(ErrorCode.FOLLOW_USER_CLIENT_FAIL);
            }
            FollowersVO vo = objectMapper.convertValue(result.getData(), FollowersVO.class);
            list1.add(vo);
        }
        return list1;
    }

    @Override
    public boolean isFollowing(Long userId, Long targetUserId) {
        FollowInfo relation = followMapper.selectRelation(userId, targetUserId);
        return relation != null && relation.getIsDelete() != null && relation.getIsDelete() == 0;
    }

    @Override
    public FollowCountsVO getCounts(Long userId) {
        return new FollowCountsVO(followMapper.countFollowing(userId), followMapper.countFollowers(userId));
    }
}
