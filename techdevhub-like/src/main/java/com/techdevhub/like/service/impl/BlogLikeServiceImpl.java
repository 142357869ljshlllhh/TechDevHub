package com.techdevhub.like.service.impl;

import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.like.client.BlogClient;
import com.techdevhub.like.dto.BlogCounterAdjustRequest;
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
    public void like(Long userId, Long blogId) {
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
        if (needIncrease) {
            Result result = blogClient.adjustLikeCount(blogId, new BlogCounterAdjustRequest(1));
            if (result == null || result.getCode() == null || result.getCode() != 200) {
                throw new BusinessException(ErrorCode.LIKE_CREATE_FAILED);
            }
        }
        stringRedisTemplate.opsForSet().add(BLOG_LIKED_USERS_KEY + blogId, String.valueOf(userId));
        stringRedisTemplate.opsForSet().add(USER_LIKED_BLOGS_KEY + userId, String.valueOf(blogId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlike(Long userId, Long blogId) {
        BlogLikeInfo relation = blogLikeMapper.selectRelation(userId, blogId);
        if (relation == null || relation.getIsDelete() == null || relation.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.LIKE_RELATION_NOT_FOUND);
        }
        if (blogLikeMapper.updateDeleteStatus(relation.getId(), 1) == 0) {
            throw new BusinessException(ErrorCode.LIKE_CANCEL_FAILED);
        }
        Result result = blogClient.adjustLikeCount(blogId, new BlogCounterAdjustRequest(-1));
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new BusinessException(ErrorCode.LIKE_CANCEL_FAILED);
        }
        stringRedisTemplate.opsForSet().remove(BLOG_LIKED_USERS_KEY + blogId, String.valueOf(userId));
        stringRedisTemplate.opsForSet().remove(USER_LIKED_BLOGS_KEY + userId, String.valueOf(blogId));
    }
}
