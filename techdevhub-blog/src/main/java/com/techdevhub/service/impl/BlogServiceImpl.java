package com.techdevhub.service.impl;

import com.techdevhub.client.UserProfileClient;
import com.techdevhub.client.vo.UserProfileVO;
import com.techdevhub.dto.BlogCounterAdjustDTO;
import com.techdevhub.dto.BlogInsertDTO;
import com.techdevhub.dto.BlogPageSelectDTO;
import com.techdevhub.dto.BlogUpdateDTO;
import com.techdevhub.entity.BlogInfo;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.mapper.BlogMapper;
import com.techdevhub.result.PageResult;
import com.techdevhub.result.Result;
import com.techdevhub.service.BlogService;
import com.techdevhub.util.SnowflakeIdGenerator;
import com.techdevhub.vo.BlogDetailVO;
import com.techdevhub.vo.BlogSummaryVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {
    private final BlogMapper blogMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final UserProfileClient userProfileClient;

    private static final String BLOG_VIEW_KEY = "blog:view:";
    private static final String BLOG_VIEW_WINDOW_KEY = "blog:view:window:";
    private static final String BLOG_LIKE_KEY = "blog:like:";
    private static final String BLOG_COMMENT_KEY = "blog:comment:";
    private static final String BLOG_HOT_RANK_KEY = "blog:hot:rank";
    private static final String USER_PROFILE_CACHE_KEY = "user:profile:";
    private static final String BLOG_DETAIL_LOCK_KEY = "blog:detail:lock:";
    private static final String BLOG_DETAIL_CACHE_KEY = "blog:detail:";
    private static final String BLOG_DETAIL_NULL_MARK = "NULL";
    private static final int HOT_VIEW_DELTA_THRESHOLD = 5;
    private static final long HOT_DETAIL_CACHE_TTL_MINUTES = 20;
    private static final long DETAIL_CACHE_BASE_TTL_SECONDS = 20 * 60;
    private static final long DETAIL_CACHE_TTL_JITTER_SECONDS = 180;
    private static final long DETAIL_CACHE_NULL_TTL_SECONDS = 60;

    private BloomFilter<CharSequence> blogBloomFilter;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    @PostConstruct
    public void initBloomFilter() {
        blogBloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                1_000_000,
                0.01
        );
        List<Long> ids = blogMapper.selectAllPublishedIds();
        for (Long id : ids) {
            blogBloomFilter.put(String.valueOf(id));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlogDetailVO blogInsert(Long userId, BlogInsertDTO dto){
        BlogInfo blogInfo = new BlogInfo();
        Long id = snowflakeIdGenerator.nextId();
        blogInfo.setId(id);
        blogInfo.setUserId(userId);
        blogInfo.setTitle(dto.getTitle());
        blogInfo.setContent(dto.getContent());
        blogInfo.setCategoryId(dto.getCategoryId());
        if(blogMapper.insert(blogInfo) == 0){
            throw new BusinessException(ErrorCode.BLOG_INSERT_FAILED);
        }
        stringRedisTemplate.opsForValue().set(BLOG_VIEW_KEY+id, "0");
        stringRedisTemplate.opsForValue().set(BLOG_LIKE_KEY+id, "0");
        stringRedisTemplate.opsForValue().set(BLOG_COMMENT_KEY+id, "0");
        stringRedisTemplate.opsForZSet().add(BLOG_HOT_RANK_KEY,id.toString(),0);
        if (blogBloomFilter != null) {
            blogBloomFilter.put(String.valueOf(id));
        }
        BlogDetailVO blogDetailVO = new BlogDetailVO();
        blogDetailVO.setId(id);
        blogDetailVO.setTitle(dto.getTitle());
        blogDetailVO.setContent(dto.getContent());
        blogDetailVO.setCategoryId(dto.getCategoryId());
        blogDetailVO.setUserId(userId);
        blogDetailVO.setAuthorUsername(resolveAuthorUsername(userId));
        blogDetailVO.setCommentCount(0);
        blogDetailVO.setLikeCount(0);
        blogDetailVO.setViewCount(0);
        return blogDetailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlogDetailVO blogUpdate(Long userId,Long id, BlogUpdateDTO dto){
        if((dto.getTitle()== null|| dto.getTitle().trim() == null) &&  (dto.getContent() == null || dto.getContent().trim() == null  ) && (dto.getCategoryId() == null)){
            throw new BusinessException(ErrorCode.BLOG_INSERT_FAILED);
        }
        BlogInfo blogInfo = requireOwnedBlog(userId, id);
        String title = StringUtils.hasText(dto.getTitle()) ? dto.getTitle().trim() : blogInfo.getTitle();
        String content = StringUtils.hasText(dto.getContent()) ? dto.getContent().trim() : blogInfo.getContent();
        Integer categoryId = dto.getCategoryId() != null ? dto.getCategoryId() : blogInfo.getCategoryId();
        if (title.equals(blogInfo.getTitle()) && content.equals(blogInfo.getContent()) && categoryId.equals(blogInfo.getCategoryId())) {
            throw new BusinessException(ErrorCode.BLOG_CONTENT_NOT_CHANGED);
        }
        blogMapper.updateBlog(id, title, content, categoryId);
        return toDetail(blogMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void blogDelete(Long currentUserId, Long blogId) {
        requireOwnedBlog(currentUserId, blogId);
        blogMapper.logicDelete(blogId);
    }

    @Override
    public BlogDetailVO detail(Long blogId) {
        if (blogBloomFilter != null && !blogBloomFilter.mightContain(String.valueOf(blogId))) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }

        String cacheKey = BLOG_DETAIL_CACHE_KEY + blogId;
        String lockKey = BLOG_DETAIL_LOCK_KEY + blogId;

        BlogDetailVO detail = getDetailFromCache(cacheKey);
        if (detail == null) {
            String lockValue = UUID.randomUUID().toString();
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(locked)) {
                try {
                    detail = getDetailFromCache(cacheKey);
                    if (detail == null) {
                        BlogInfo blogInfo = blogMapper.selectById(blogId);
                        if (blogInfo == null
                                || (blogInfo.getIsDelete() != null && blogInfo.getIsDelete() == 1)
                                || blogInfo.getStatus() == null
                                || blogInfo.getStatus() != 1) {
                            stringRedisTemplate.opsForValue().set(
                                    cacheKey,
                                    BLOG_DETAIL_NULL_MARK,
                                    DETAIL_CACHE_NULL_TTL_SECONDS,
                                    TimeUnit.SECONDS
                            );
                            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
                        }
                        detail = toDetail(blogInfo);
                        long ttl = DETAIL_CACHE_BASE_TTL_SECONDS
                                + ThreadLocalRandom.current().nextLong(DETAIL_CACHE_TTL_JITTER_SECONDS + 1);
                        stringRedisTemplate.opsForValue().set(cacheKey, toJson(detail), ttl, TimeUnit.SECONDS);
                    }
                } finally {
                    stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
                }
            } else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                detail = getDetailFromCache(cacheKey);
                if (detail == null) {
                    BlogInfo fallback = requirePublishedBlog(blogId);
                    detail = toDetail(fallback);
                }
            }
        }

        stringRedisTemplate.opsForValue().increment(BLOG_VIEW_KEY + blogId);
        stringRedisTemplate.opsForValue().increment(BLOG_VIEW_WINDOW_KEY + blogId);
        stringRedisTemplate.expire(BLOG_VIEW_WINDOW_KEY + blogId, 120, TimeUnit.SECONDS);
        stringRedisTemplate.opsForZSet().incrementScore(BLOG_HOT_RANK_KEY, String.valueOf(blogId), 1D);

        detail.setViewCount(readCounter(BLOG_VIEW_KEY + blogId));
        detail.setLikeCount(readCounter(BLOG_LIKE_KEY + blogId));
        detail.setCommentCount(readCounter(BLOG_COMMENT_KEY + blogId));
        return detail;
    }

    @Override
    public PageResult<BlogSummaryVO> page(BlogPageSelectDTO dto) {
        long pageNum = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1L : dto.getPageNum();
        long pageSize = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10L : dto.getPageSize();
        long offset = (pageNum - 1) * pageSize;
        List<BlogInfo> records = blogMapper.selectPage(offset, pageSize, dto.getCategoryId(), dto.getKeyword(), dto.getUserId());
        Long total = blogMapper.countPage(dto.getCategoryId(), dto.getKeyword(), dto.getUserId());
        List<BlogSummaryVO> data = records.stream().map(this::toSummary).toList();
        return PageResult.of(total, pageNum, pageSize, data);
    }

    @Override
    public List<BlogSummaryVO> hotTop10() {
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(BLOG_HOT_RANK_KEY, 0, 9);
        if (CollectionUtils.isEmpty(tuples)) {
            return blogMapper.selectTopByHot(10).stream().map(this::toSummary).toList();
        }
        List<BlogSummaryVO> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple == null || tuple.getValue() == null) {
                continue;
            }
            BlogInfo blogInfo = blogMapper.selectById(Long.valueOf(tuple.getValue()));
            if (blogInfo != null && blogInfo.getIsDelete() != null && blogInfo.getIsDelete() == 0 && blogInfo.getStatus() == 1) {
                result.add(toSummary(blogInfo));
            }
        }
        return result;
    }

    @Override
    public List<BlogSummaryVO> currentUserBlogs(Long currentUserId) {
        return blogMapper.selectByUserId(currentUserId).stream().map(this::toSummary).toList();
    }

    @Override
    public void adjustLikeCount(Long blogId, BlogCounterAdjustDTO dto) {
        adjustCounter(BLOG_LIKE_KEY + blogId, dto.getDelta());
        stringRedisTemplate.opsForZSet().incrementScore(BLOG_HOT_RANK_KEY, String.valueOf(blogId), dto.getDelta() * 2D);
    }

    @Override
    public void adjustCommentCount(Long blogId, BlogCounterAdjustDTO dto) {
        adjustCounter(BLOG_COMMENT_KEY + blogId, dto.getDelta());
        stringRedisTemplate.opsForZSet().incrementScore(BLOG_HOT_RANK_KEY, String.valueOf(blogId), dto.getDelta() * 3D);
    }

    @Override
    public List<BlogSummaryVO> pendingBlogs() {
        return blogMapper.selectPendingBlogs().stream().map(this::toSummary).toList();
    }

    @Override
    public void changeStatus(Long blogId, Integer status) {
        blogMapper.updateStatus(blogId, status);
    }







    private Long extractBlogId(String redisKey) {
        return Long.valueOf(redisKey.substring(redisKey.lastIndexOf(':') + 1));
    }

    @Scheduled(fixedDelay = 300000)
    public void flushCountersToDatabase() {
        Set<String> keys = stringRedisTemplate.keys("blog:*:*");
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        keys.stream()
                .filter(key -> key.startsWith(BLOG_VIEW_KEY) || key.startsWith(BLOG_LIKE_KEY) || key.startsWith(BLOG_COMMENT_KEY))
                .map(this::extractBlogId)
                .distinct()
                .forEach(this::flushSingleBlogCounters);
    }

    @Scheduled(fixedDelay = 30000)
    public void cacheHotBlogsByViewDelta() {
        Set<String> keys = stringRedisTemplate.keys(BLOG_VIEW_WINDOW_KEY + "*");
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Long blogId = extractBlogId(key);
            Integer delta = readCounter(key);
            if (delta < HOT_VIEW_DELTA_THRESHOLD) {
                continue;
            }
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLOG_DETAIL_CACHE_KEY + blogId))) {
                continue;
            }
            BlogInfo blogInfo = blogMapper.selectById(blogId);
            if (blogInfo == null
                    || (blogInfo.getIsDelete() != null && blogInfo.getIsDelete() == 1)
                    || blogInfo.getStatus() == null
                    || blogInfo.getStatus() != 1) {
                continue;
            }
            BlogDetailVO detailVO = toDetail(blogInfo);
            try {
                String json = objectMapper.writeValueAsString(detailVO);
                stringRedisTemplate.opsForValue().set(
                        BLOG_DETAIL_CACHE_KEY + blogId,
                        json,
                        HOT_DETAIL_CACHE_TTL_MINUTES,
                        TimeUnit.MINUTES
                );
            } catch (JsonProcessingException ignored) {
                // Skip cache warming if serialization fails. The normal DB path still works.
            }
        }
    }

    private void flushSingleBlogCounters(Long blogId) {
        BlogInfo blogInfo = blogMapper.selectById(blogId);
        if (blogInfo == null) {
            return;
        }
        blogMapper.updateCounters(
                blogId,
                readCounter(BLOG_LIKE_KEY + blogId),
                readCounter(BLOG_VIEW_KEY + blogId),
                readCounter(BLOG_COMMENT_KEY + blogId)
        );
    }


    private void adjustCounter(String key, Integer delta) {
        String current = stringRedisTemplate.opsForValue().get(key);
        long base = current == null ? 0L : Long.parseLong(current);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(Math.max(base + delta, 0L)));
    }

    private BlogSummaryVO toSummary(BlogInfo blogInfo) {
        String content = blogInfo.getContent() == null ? "" : blogInfo.getContent();
        String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;
        return new BlogSummaryVO(
                blogInfo.getId(),
                blogInfo.getUserId(),
                resolveAuthorUsername(blogInfo.getUserId()),
                blogInfo.getTitle(),
                preview,
                blogInfo.getCategoryId(),
                readCounter(BLOG_LIKE_KEY + blogInfo.getId()),
                readCounter(BLOG_VIEW_KEY + blogInfo.getId()),
                readCounter(BLOG_COMMENT_KEY + blogInfo.getId()),
                blogInfo.getCreateTime()
        );
    }


    private BlogInfo requireOwnedBlog(Long currentUserId, Long blogId) {
        BlogInfo blogInfo = blogMapper.selectById(blogId);
        if (blogInfo == null || (blogInfo.getIsDelete() != null && blogInfo.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        if (!blogInfo.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.BLOG_FORBIDDEN);
        }
        return blogInfo;
    }

    private BlogDetailVO toDetail(BlogInfo blogInfo) {
        return new BlogDetailVO(
                blogInfo.getId(),
                blogInfo.getUserId(),
                resolveAuthorUsername(blogInfo.getUserId()),
                blogInfo.getTitle(),
                blogInfo.getContent(),
                blogInfo.getCategoryId(),
                readCounter(BLOG_LIKE_KEY + blogInfo.getId()),
                readCounter(BLOG_VIEW_KEY + blogInfo.getId()),
                readCounter(BLOG_COMMENT_KEY + blogInfo.getId()),
                blogInfo.getCreateTime(),
                blogInfo.getUpdateTime()
        );
    }


    private Integer readCounter(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private Integer defaultCount(Integer count) {
        return count == null ? 0 : count;
    }

    private BlogInfo requirePublishedBlog(Long blogId) {
        BlogInfo blogInfo = blogMapper.selectById(blogId);
        if (blogInfo == null || (blogInfo.getIsDelete() != null && blogInfo.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        if (blogInfo.getStatus() == null || blogInfo.getStatus() != 1) {
            throw new BusinessException(ErrorCode.BLOG_NOT_PULL);
        }
        return blogInfo;
    }

    private BlogDetailVO getDetailFromCache(String cacheKey) {
        String value = stringRedisTemplate.opsForValue().get(cacheKey);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (BLOG_DETAIL_NULL_MARK.equals(value)) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        try {
            return objectMapper.readValue(value, BlogDetailVO.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String toJson(BlogDetailVO blogDetailVO) {
        try {
            return objectMapper.writeValueAsString(blogDetailVO);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private String resolveAuthorUsername(Long userId) {
        String profile = stringRedisTemplate.opsForValue().get(USER_PROFILE_CACHE_KEY + userId);
        if (!StringUtils.hasText(profile)) {
            return fetchFromUserServiceAndCache(userId);
        }
        try {
            String username = objectMapper.readTree(profile).path("username").asText(null);
            if (StringUtils.hasText(username)) {
                return username;
            }
            return fetchFromUserServiceAndCache(userId);
        } catch (JsonProcessingException ignored) {
            return fetchFromUserServiceAndCache(userId);
        }
    }

    private String fetchFromUserServiceAndCache(Long userId) {
        try {
            Result result = userProfileClient.getProfile(userId);
            if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
                return null;
            }
            UserProfileVO userProfileVO = objectMapper.convertValue(result.getData(), UserProfileVO.class);
            if (!StringUtils.hasText(userProfileVO.getUsername())) {
                return null;
            }
            stringRedisTemplate.opsForValue().set(USER_PROFILE_CACHE_KEY + userId, objectMapper.writeValueAsString(userProfileVO));
            return userProfileVO.getUsername();
        } catch (Exception ignored) {
            return null;
        }
    }
}
