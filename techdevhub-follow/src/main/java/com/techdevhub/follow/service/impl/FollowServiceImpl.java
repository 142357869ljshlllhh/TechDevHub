package com.techdevhub.follow.service.impl;

import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.follow.entity.FollowInfo;
import com.techdevhub.follow.mapper.FollowMapper;
import com.techdevhub.follow.service.FollowService;
import com.techdevhub.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private static final String FOLLOWING_KEY = "follow:user:";
    private static final String FOLLOWERS_KEY = "follow:fans:";

    private final FollowMapper followMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate stringRedisTemplate;

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
}
