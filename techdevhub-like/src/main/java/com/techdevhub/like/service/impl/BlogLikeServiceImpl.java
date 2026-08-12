package com.techdevhub.like.service.impl;

import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.like.client.BlogClient;
import com.techdevhub.like.dto.BlogCounterAdjustRequest;
import com.techdevhub.like.dto.LikeResult;
import com.techdevhub.like.entity.BlogLikeInfo;
import com.techdevhub.like.mapper.BlogLikeMapper;
import com.techdevhub.like.service.BlogLikeService;
import com.techdevhub.result.Result;
import com.techdevhub.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlogLikeServiceImpl implements BlogLikeService {

    private static final String BLOG_LIKED_USERS_KEY = "blog:like:users:";
    private static final String USER_LIKED_BLOGS_KEY = "user:like:blogs:";

    private final BlogLikeMapper blogLikeMapper;
    private final BlogClient blogClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeResult like(Long userId, Long blogId) {
        BlogLikeInfo relation = blogLikeMapper.selectRelation(userId, blogId);
        boolean needIncrease = false;
        if (relation == null) {
            BlogLikeInfo blogLikeInfo = new BlogLikeInfo();
            blogLikeInfo.setId(snowflakeIdGenerator.nextId());
            blogLikeInfo.setUserId(userId);
            blogLikeInfo.setBlogId(blogId);
            blogLikeInfo.setIsDelete(0);
            if (blogLikeMapper.insert(blogLikeInfo) == 0) {
                throw new BusinessException(ErrorCode.LIKE_CREATE_FAILED);
            }
            needIncrease = true;
        } else if (relation.getIsDelete() != null && relation.getIsDelete() == 1) {
            if (blogLikeMapper.updateDeleteStatus(relation.getId(), 0) == 0) {
                throw new BusinessException(ErrorCode.LIKE_CREATE_FAILED);
            }
            needIncrease = true;
        }
        // 仅在“点赞关系从无效变有效”时增减博客服务的展示计数，
        // 保持幂等：重复点赞不会让 like_count 凭空 +1。
        if (needIncrease) {
            try {
                blogClient.adjustLikeCount(blogId, new BlogCounterAdjustRequest(1));
            } catch (Exception ignored) {
                // 展示计数最终由 DB 真相校准，单次同步失败不影响主流程
            }
        }
        stringRedisTemplate.opsForSet().add(BLOG_LIKED_USERS_KEY + blogId, String.valueOf(userId));
        stringRedisTemplate.opsForSet().add(USER_LIKED_BLOGS_KEY + userId, String.valueOf(blogId));
        // 以 blog_like_info 表实时统计作为权威点赞总数返回，前端据此校准按钮数字
        Long likeCount = blogLikeMapper.countByBlogId(blogId);
        return new LikeResult(true, likeCount == null ? 0L : likeCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeResult unlike(Long userId, Long blogId) {
        BlogLikeInfo relation = blogLikeMapper.selectRelation(userId, blogId);
        if (relation == null || relation.getIsDelete() == null || relation.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.LIKE_RELATION_NOT_FOUND);
        }
        if (blogLikeMapper.updateDeleteStatus(relation.getId(), 1) == 0) {
            throw new BusinessException(ErrorCode.LIKE_CANCEL_FAILED);
        }
        try {
            blogClient.adjustLikeCount(blogId, new BlogCounterAdjustRequest(-1));
        } catch (Exception ignored) {
            // 展示计数最终由 DB 真相校准，单次同步失败不影响主流程
        }
        stringRedisTemplate.opsForSet().remove(BLOG_LIKED_USERS_KEY + blogId, String.valueOf(userId));
        stringRedisTemplate.opsForSet().remove(USER_LIKED_BLOGS_KEY + userId, String.valueOf(blogId));
        Long likeCount = blogLikeMapper.countByBlogId(blogId);
        return new LikeResult(false, likeCount == null ? 0L : likeCount);
    }

    @Override
    public boolean isLiked(Long userId, Long blogId) {
        BlogLikeInfo relation = blogLikeMapper.selectRelation(userId, blogId);
        return relation != null && relation.getIsDelete() != null && relation.getIsDelete() == 0;
    }

    @Override
    public java.util.Map<Long, Boolean> batchIsLiked(Long userId, java.util.List<Long> blogIds) {
        java.util.Map<Long, Boolean> result = new java.util.HashMap<>();
        if (blogIds == null || blogIds.isEmpty()) {
            return result;
        }
        // 默认全部未赞
        for (Long blogId : blogIds) {
            result.put(blogId, Boolean.FALSE);
        }
        // 用 DB 作为权威数据源（Redis 集合在并发下可能短暂不一致，DB 永远准确）
        java.util.List<Long> liked = blogLikeMapper.selectLikedBlogIds(userId, blogIds);
        if (liked != null) {
            for (Long blogId : liked) {
                result.put(blogId, Boolean.TRUE);
            }
        }
        return result;
    }
}
