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
        return toValidProfiles(followMapper.getFollowers(userId));
    }

    @Override
    public List<FollowersVO> getFollowing(Long userId) {
        return toValidProfiles(followMapper.getFollowing(userId));
    }

    /**
     * id 列表 → 有效用户资料 VO。
     * 为什么走批量端点（POST /users/profiles/batch）：
     * 1) user 侧在该端点统一过滤已注销(is_delete=1)/封禁用户——账号软删后的
     *    残留关注关系在这里被天然挡住，幽灵粉丝既不出现在列表也不计入计数；
     * 2) 一次 RPC 替代逐条 getProfile 的 N+1；
     * 3) user 服务故障/返回异常时返回空列表而非抛错——宁可短暂显示空列表，
     *    不给用户整页报错（原实现对单个资料失败就 1303 炸全列表）。
     */
    private List<FollowersVO> toValidProfiles(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        Result result = userClient.batchGetProfiles(userIds);
        if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
            return new ArrayList<>();
        }
        List<?> profiles = (List<?>) result.getData();
        List<FollowersVO> vos = new ArrayList<>(profiles.size());
        for (Object profile : profiles) {
            vos.add(objectMapper.convertValue(profile, FollowersVO.class));
        }
        return vos;
    }

    @Override
    public boolean isFollowing(Long userId, Long targetUserId) {
        FollowInfo relation = followMapper.selectRelation(userId, targetUserId);
        return relation != null && relation.getIsDelete() != null && relation.getIsDelete() == 0;
    }

    @Override
    public FollowCountsVO getCounts(Long userId) {
        // 计数与列表同一套有效性口径（批量校验过滤已注销/封禁），
        // 保证"2 粉丝"打开粉丝列表就真有 2 个人可看
        return new FollowCountsVO(
                (long) toValidProfiles(followMapper.getFollowing(userId)).size(),
                (long) toValidProfiles(followMapper.getFollowers(userId)).size());
    }
}
