package com.techdevhub.service.impl;

import com.techdevhub.client.UserProfileClient;
import com.techdevhub.client.vo.UserProfileVO;
import com.techdevhub.context.UserContext;
import com.techdevhub.dto.BlogCounterAdjustDTO;
import com.techdevhub.dto.BlogInsertDTO;
import com.techdevhub.dto.BlogPageSelectDTO;
import com.techdevhub.dto.BlogUpdateDTO;
import com.techdevhub.entity.BlogInfo;
import com.techdevhub.entity.RagIndexStatus;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.mapper.BlogMapper;
import com.techdevhub.result.PageResult;
import com.techdevhub.result.Result;
import com.techdevhub.service.BlogService;
import com.techdevhub.service.ModerationFlowService;
import com.techdevhub.util.SnowflakeIdGenerator;
import com.techdevhub.vo.BlogDetailVO;
import com.techdevhub.vo.BlogSummaryVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {
    private final BlogMapper blogMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final UserProfileClient userProfileClient;
    private final ModerationFlowService moderationFlowService;
    private final com.techdevhub.service.RagIngestService ragIngestService;
    private final com.techdevhub.mapper.RagIndexStatusMapper ragIndexStatusMapper;

    private static final String BLOG_VIEW_KEY = "blog:view:";
    private static final String BLOG_VIEW_WINDOW_KEY = "blog:view:window:";
    private static final String BLOG_LIKE_KEY = "blog:like:";
    private static final String BLOG_COMMENT_KEY = "blog:comment:";
    private static final String BLOG_HOT_RANK_KEY = "blog:hot:rank";
    private static final String USER_PROFILE_CACHE_KEY = "user:profile:";
    private static final String BLOG_DETAIL_LOCK_KEY = "blog:detail:lock:";
    private static final String BLOG_DETAIL_CACHE_KEY = "blog:detail:v2:"; // v2：修复草稿越权缓存泄露后升版，作废旧键
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

    @Autowired
    private Executor asyncExecutor;

    @PostConstruct
    public void initBloomFilter() {
        blogBloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                1_000_000,
                0.01
        );
        List<Long> ids = blogMapper.selectAllIds();
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
        // 先发后审：status=0 审核中，ModerationFlowService 审核后流转 1（通过）/
        // 2（拒绝）；review 或服务故障停在 0 等人工（契约映射见 docs/java_ai_integration_plan.md §2.2）
        blogInfo.setStatus(0);
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
        // 审核必须等事务提交后再触发（异步线程读不到未提交数据），
        // 且 30s 级 LLM 调用不能占住发布请求线程
        triggerModerationAfterCommit(blogInfo);
        return blogDetailVO;
    }

    /**
     * 事务提交后异步触发审核。
     * 为什么用 afterCommit 而不是直接 @Async：事务内提交异步任务，任务可能在
     * commit 前执行，异步线程读库会看不到这条博客；事务回滚时审核也不该发生。
     */
    private void triggerModerationAfterCommit(BlogInfo blog) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    moderationFlowService.moderate(blog);
                }
            });
        } else {
            // 无事务上下文（如单测直调）时兜底直接触发
            moderationFlowService.moderate(blog);
        }
    }

    /** 事务提交后异步移除向量语料（与审核触发同一 afterCommit 模式，防事务回滚误删） */
    private void triggerRagRemoveAfterCommit(Long blogId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ragIngestService.deleteAsync(blogId);
                }
            });
        } else {
            ragIngestService.deleteAsync(blogId);
        }
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
        Long categoryId = dto.getCategoryId() != null ? dto.getCategoryId() : blogInfo.getCategoryId();
        if (title.equals(blogInfo.getTitle()) && content.equals(blogInfo.getContent()) && categoryId.equals(blogInfo.getCategoryId())) {
            throw new BusinessException(ErrorCode.BLOG_CONTENT_NOT_CHANGED);
        }
        blogMapper.updateBlog(id, title, content, categoryId);
        // 修复：文章更新后必须清除 Redis 详情缓存，否则详情接口会一直返回更新前的旧内容
        // （表现为“提示修改成功，但文章内容并未改变”）。
        stringRedisTemplate.delete(BLOG_DETAIL_CACHE_KEY + id);
        // 内容变更需要重审（标题/正文不变只调分类的场景不重审，减少 LLM 消耗）
        if (!title.equals(blogInfo.getTitle()) || !content.equals(blogInfo.getContent())) {
            BlogInfo reModerate = new BlogInfo();
            reModerate.setId(id);
            reModerate.setUserId(userId);
            reModerate.setTitle(title);
            reModerate.setContent(content);
            // 更新重审语义：改回 status=0（审核中，对用户不可见），通过后恢复发布
            blogMapper.updateStatus(id, 0);
            // 编辑即出库（M10）：立即移除旧全文的向量语料（旧内容已不可见，
            // 不允许 RAG 在重审窗口期继续引用它）；重审通过后 ingest 会以
            // 整篇替换语义重建新全文。afterCommit：事务回滚则不删。
            triggerRagRemoveAfterCommit(id);
            triggerModerationAfterCommit(reModerate);
        }
        return toDetail(blogMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void blogDelete(Long currentUserId, Long blogId) {
        requireOwnedBlog(currentUserId, blogId);
        blogMapper.logicDelete(blogId);
        // 修复：删除后同样清除详情缓存，避免已删除文章仍可从缓存读到
        stringRedisTemplate.delete(BLOG_DETAIL_CACHE_KEY + blogId);
        // 向量语料同步出库（M10）：防止已删除文章仍被 RAG 检索引用
        triggerRagRemoveAfterCommit(blogId);
    }

    @Override
    public BlogDetailVO detail(Long blogId, Long currentUserId) {
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
                                || blogInfo.getStatus() == null) {
                            stringRedisTemplate.opsForValue().set(
                                    cacheKey,
                                    BLOG_DETAIL_NULL_MARK,
                                    DETAIL_CACHE_NULL_TTL_SECONDS,
                                    TimeUnit.SECONDS
                            );
                            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
                        }
                        if (blogInfo.getStatus() == 2) {
                            // 已驳回/下架：对所有人不可见（保持原语义）
                            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
                        }
                        if (blogInfo.getStatus() != 1) {
                            // 审核中/草稿（status=0）：仅作者本人可见，且绝不进详情缓存——
                            // 否则任何匿名用户都能借缓存读到他人未发布全文
                            if (currentUserId == null || !currentUserId.equals(blogInfo.getUserId())) {
                                throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
                            }
                            return toDetail(blogInfo);
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
        // 批量解析作者名：N 条博客无论多少不同作者，只发 1 次 Feign 批量请求（消除 N+1）
        Map<Long, String> usernameMap = batchResolveAuthorUsernames(
                records.stream().map(BlogInfo::getUserId).collect(Collectors.toSet()));
        List<BlogSummaryVO> data = records.stream()
                .map(record -> CompletableFuture.supplyAsync(() -> toSummary(record, usernameMap), asyncExecutor))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
        return PageResult.of(total, pageNum, pageSize, data);
    }

    @Override
    public List<BlogSummaryVO> hotTop10() {
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(BLOG_HOT_RANK_KEY, 0, 9);
        List<BlogInfo> blogs = new ArrayList<>();
        if (CollectionUtils.isEmpty(tuples)) {
            blogs.addAll(blogMapper.selectTopByHot(10));
        } else {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple == null || tuple.getValue() == null) {
                    continue;
                }
                BlogInfo blogInfo = blogMapper.selectById(Long.valueOf(tuple.getValue()));
                if (blogInfo != null && blogInfo.getIsDelete() != null && blogInfo.getIsDelete() == 0 && blogInfo.getStatus() == 1) {
                    blogs.add(blogInfo);
                }
            }
        }
        if (blogs.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> usernameMap = batchResolveAuthorUsernames(
                blogs.stream().map(BlogInfo::getUserId).collect(Collectors.toSet()));
        return blogs.stream().map(b -> toSummary(b, usernameMap)).toList();
    }

    @Override
    public List<BlogSummaryVO> currentUserBlogs(Long currentUserId) {
        List<BlogInfo> records = blogMapper.selectByUserId(currentUserId);
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> usernameMap = batchResolveAuthorUsernames(
                records.stream().map(BlogInfo::getUserId).collect(Collectors.toSet()));
        return records.stream().map(b -> toSummary(b, usernameMap)).toList();
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
        // 审核状态变化会改变文章可见性，清除详情缓存（含可能存在的 NULL 标记）
        stringRedisTemplate.delete(BLOG_DETAIL_CACHE_KEY + blogId);
        // 向量语料生命周期（M10）：下架/驳回 → 出库；转人工被采纳（人工通过）→ 入库。
        // 与 AI 审核路径（ModerationFlowService 直接 updateStatus）互不重叠，无双重触发
        if (Integer.valueOf(2).equals(status)) {
            ragIngestService.deleteAsync(blogId);
        } else if (Integer.valueOf(1).equals(status)) {
            BlogInfo fresh = blogMapper.selectById(blogId);
            if (fresh != null) {
                ragIngestService.ingest(fresh);
            }
        }
    }

    @Override
    public Long createDraft(Long userId, String title, String content) {
        BlogInfo draft = new BlogInfo();
        Long id = snowflakeIdGenerator.nextId();
        draft.setId(id);
        draft.setUserId(userId);
        draft.setTitle(title);
        draft.setContent(content);
        draft.setStatus(0); // 草稿=审核中态复用，不触发审核也不进热榜
        if (blogMapper.insert(draft) == 0) {
            throw new BusinessException(ErrorCode.BLOG_INSERT_FAILED);
        }
        // 计数器与发布路径同款初始化：草稿日后发布时计数器已就绪，避免 NPE
        stringRedisTemplate.opsForValue().set(BLOG_VIEW_KEY + id, "0");
        stringRedisTemplate.opsForValue().set(BLOG_LIKE_KEY + id, "0");
        stringRedisTemplate.opsForValue().set(BLOG_COMMENT_KEY + id, "0");
        if (blogBloomFilter != null) {
            blogBloomFilter.put(String.valueOf(id));
        }
        return id;
    }

    @Override
    public List<BlogInfo> draftsOf(Long userId) {
        // status=0 即"未发布"：涵盖用户手存草稿与审核中文章的集合语义，
        // 对 get_drafts 工具来说"还没发出去的"就是草稿
        return blogMapper.selectByUserId(userId).stream()
                .filter(b -> b.getStatus() != null && b.getStatus() == 0 && (b.getIsDelete() == null || b.getIsDelete() == 0))
                .toList();
    }

    @Override
    public int recheckBlogs(List<Long> blogIds) {
        // 只重审存在且未删除的文章（被删除的文章重审无意义）
        List<BlogInfo> blogs = blogIds.stream()
                .map(blogMapper::selectById)
                .filter(b -> b != null && (b.getIsDelete() == null || b.getIsDelete() == 0))
                .toList();
        moderationFlowService.recheck(blogs);
        return blogs.size();
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
        return toSummary(blogInfo, null);
    }

    private BlogSummaryVO toSummary(BlogInfo blogInfo, Map<Long, String> usernameMap) {
        String content = blogInfo.getContent() == null ? "" : blogInfo.getContent();
        String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;
        // 优先用批量解析结果，未覆盖时降级为单条兜底（保持原行为）
        String username = usernameMap != null ? usernameMap.get(blogInfo.getUserId()) : null;
        if (username == null) {
            username = resolveAuthorUsername(blogInfo.getUserId());
        }
        BlogSummaryVO vo = new BlogSummaryVO(
                blogInfo.getId(),
                blogInfo.getUserId(),
                username,
                blogInfo.getTitle(),
                preview,
                blogInfo.getCategoryId(),
                blogInfo.getStatus(),
                readCounter(BLOG_LIKE_KEY + blogInfo.getId()),
                readCounter(BLOG_VIEW_KEY + blogInfo.getId()),
                readCounter(BLOG_COMMENT_KEY + blogInfo.getId()),
                blogInfo.getCreateTime()
        );
        return vo;
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

    @Override
    public void assertAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // isAdmin 来自 JWT 的 isAdmin claim，登录时按 user_info.status==1 写入，
        // 已由 JwtInterceptor 解析进 UserContext，无需每次走 Feign 远程判定。
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
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
        if (blogInfo == null || (blogInfo.getIsDelete() != null && blogInfo.getIsDelete() == 1)
                || blogInfo.getStatus() == null || blogInfo.getStatus() != 1) {
            // 只有已发布(status=1)才可见——未发布内容对任何路径都视同不存在，不泄露存在性
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
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

    /**
     * 批量解析作者名：先批量查 Redis 缓存，未命中的只发一次 Feign 批量请求，
     * 把 N 条不同作者的并发 N+1 RPC 收敛成 1 次，并回填缓存（含同页其余同作者博客）。
     */
    private Map<Long, String> batchResolveAuthorUsernames(Set<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        Set<Long> missIds = new HashSet<>();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            String cached = stringRedisTemplate.opsForValue().get(USER_PROFILE_CACHE_KEY + userId);
            if (StringUtils.hasText(cached)) {
                try {
                    String username = objectMapper.readTree(cached).path("username").asText(null);
                    if (StringUtils.hasText(username)) {
                        result.put(userId, username);
                        continue;
                    }
                } catch (JsonProcessingException ignored) {
                    // 缓存内容异常，走远程回源
                }
            }
            missIds.add(userId);
        }
        if (!missIds.isEmpty()) {
            try {
                Result remote = userProfileClient.batchGetProfiles(new ArrayList<>(missIds));
                if (remote != null && remote.getCode() != null && remote.getCode() == 200 && remote.getData() != null) {
                    List<UserProfileVO> vos = objectMapper.convertValue(remote.getData(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<UserProfileVO>>() {});
                    for (UserProfileVO vo : vos) {
                        if (vo == null || vo.getId() == null) {
                            continue;
                        }
                        result.put(vo.getId(), vo.getUsername());
                        // 回填 Redis 缓存，后续请求直接命中
                        UserProfileVO cacheVo = new UserProfileVO();
                        cacheVo.setId(vo.getId());
                        cacheVo.setUsername(vo.getUsername());
                        stringRedisTemplate.opsForValue().set(USER_PROFILE_CACHE_KEY + vo.getId(),
                                objectMapper.writeValueAsString(cacheVo));
                    }
                }
            } catch (Exception ignored) {
                // 批量失败时降级为逐个兜底，不影响列表返回
            }
        }
        return result;
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

    // ---------- 向量索引管理（M10，管理员） ----------

    @Override
    public List<RagIndexStatus> ragIndexStatus(String status) {
        return ragIndexStatusMapper.selectList(status);
    }

    @Override
    public void reingestRag(Long blogId) {
        BlogInfo blog = blogMapper.selectById(blogId);
        if (blog == null || (blog.getIsDelete() != null && blog.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.BLOG_NOT_FOUND);
        }
        ragIngestService.ingest(blog);
    }

    @Override
    public int rebuildRag() {
        List<BlogInfo> blogs = blogMapper.selectPublished();
        blogs.forEach(ragIngestService::ingest);
        return blogs.size();
    }
}
