package com.techdevhub.service;

import com.techdevhub.client.AiServiceClient;
import com.techdevhub.dto.ai.AiResult;
import com.techdevhub.dto.ai.ModerationCheckRequest;
import com.techdevhub.dto.ai.ModerationResult;
import com.techdevhub.dto.ai.RecheckRequest;
import com.techdevhub.dto.ai.RecheckResponse;
import com.techdevhub.entity.BlogInfo;
import com.techdevhub.entity.BlogModeration;
import com.techdevhub.exception.AiCallException;
import com.techdevhub.mapper.BlogMapper;
import com.techdevhub.mapper.BlogModerationMapper;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 审核编排 + 业务重试状态机（与 Python 侧的重试分工见 docs/java_ai_integration_plan.md §4）：
 *   PENDING → GENERATING → OK(按 verdict 流转 status)
 *        ↓ 失败且 retryable=true
 *   指数退避(基数 2s, ×2^k, ±30% jitter) 上限 3 次；429 基数拉长到 5s
 *        ↓ 耗尽或 retryable=false
 *   GIVEUP → status 保持 0（审核中）+ review_reason=AGENT_FAILURE（管理端一键重审分组依据）
 * Python 侧只做传输层重试（≤2 次），两侧放大倍数 2×3 可控——Java 不重复做传输层重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationFlowService {

    private final AiServiceClient aiServiceClient;
    private final BlogMapper blogMapper;
    private final BlogModerationMapper moderationMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RagIngestService ragIngestService;

    private static final String BLOG_DETAIL_CACHE_KEY = "blog:detail:";
    private static final String REVIEW_REASON_AGENT_FAILURE = "AGENT_FAILURE";
    private static final int MAX_ATTEMPTS = 3;

    /** 退避基数抽成字段是为了单测能把 sleep 压到 0ms——真实运行不受影响 */
    @Setter
    private long normalBackoffBaseMs = 2000;

    /** 429 的退避基数拉长（尊重对端限流窗口，立即重试只会继续 429） */
    @Setter
    private long rateLimitBackoffBaseMs = 5000;

    /**
     * 审核一篇博客并按 verdict 流转状态（发布与更新复用）。
     * 由 BlogServiceImpl 在事务提交后以 @Async 调用——事务内调用会读不到未提交数据，
     * 且 LLM 审核 30s 级耗时会拖垮发布 RT。
     */
    @Async("asyncExecutor")
    public void moderate(BlogInfo blog) {
        // 无条件 for：体内所有分支要么 return 要么 sleep 后重试，上限判断在 attempt == MAX_ATTEMPTS——
        // 写 attempt <= MAX_ATTEMPTS 会被 IDEA 判定条件恒真
        for (int attempt = 1; ; attempt++) {
            try {
                AiResult<ModerationResult> resp = aiServiceClient.check(toCheckRequest(blog));
                ModerationResult result = resp.getData();
                if (result == null) {
                    // 理论不可达：200 一定带领域对象；防御式归为不可重试故障
                    throw AiCallException.transport("empty moderation result");
                }
                applyVerdict(blog, result);
                return;
            } catch (AiCallException e) {
                log.warn("审核失败 blogId={} attempt={}/{} code={} retryable={}",
                        blog.getId(), attempt, MAX_ATTEMPTS, e.getAiCode(), e.isRetryable());
                if (!e.isRetryable()) {
                    giveUp(blog, e); // 不可重试：重试只会浪费额度，直接挂人工
                    return;
                }
                if (attempt == MAX_ATTEMPTS) {
                    giveUp(blog, e);
                    return;
                }
                sleepQuietly(backoffMs(attempt, e.getHttpStatus()));
            }
        }
    }

    /**
     * 管理端批量重审（同步执行，T8）。
     * 对端整批 fail-fast——任一篇组件故障整批 503，AiCallException 直接抛给控制器
     * （转 503 + AI_SERVICE_TEMPORARY），管理员整批重试即可（重审幂等）。
     */
    public void recheck(List<BlogInfo> blogs) {
        List<ModerationCheckRequest> items = blogs.stream().map(this::toCheckRequest).toList();
        AiResult<RecheckResponse> resp = aiServiceClient.recheck(new RecheckRequest(items));
        RecheckResponse data = resp.getData();
        if (data == null || data.getResults() == null) {
            throw AiCallException.transport("empty recheck response");
        }
        List<ModerationResult> results = data.getResults();
        // 对端保证 results 与 items 顺序一一对应
        for (int i = 0; i < blogs.size() && i < results.size(); i++) {
            applyVerdict(blogs.get(i), results.get(i));
        }
    }

    // ---------- 状态流转 ----------

    private void applyVerdict(BlogInfo blog, ModerationResult result) {
        switch (result.getVerdict()) {
            case "approve" -> {
                changeStatus(blog.getId(), 1);
                saveRecord(blog, result, null, null);
                // 跨 Bean 调用走代理，@Async 才生效（自调用会静默退化为同步阻塞）
                ragIngestService.ingest(blog);
                log.info("审核通过 blogId={} layer={} latencyMs={}",
                        blog.getId(), result.getLayer(), result.getLatencyMs());
            }
            case "reject" -> {
                changeStatus(blog.getId(), 2);
                saveRecord(blog, result, null, null);
                log.info("审核拒绝 blogId={} reason={}", blog.getId(), result.getReason());
            }
            default -> {
                // review：保持 0，进 pendingBlogs 人工队列
                saveRecord(blog, result, null, null);
                log.info("审核转人工 blogId={} reason={}", blog.getId(), result.getReason());
            }
        }
    }

    private void giveUp(BlogInfo blog, AiCallException e) {
        // status 保持 0（审核中），管理后台按 AGENT_FAILURE 分组一键重审
        saveRecord(blog, null, e.getAiCode(), REVIEW_REASON_AGENT_FAILURE);
        log.error("审核重试耗尽 blogId={} code={}，已挂人工重审", blog.getId(), e.getAiCode());
    }

    private void changeStatus(Long blogId, Integer status) {
        blogMapper.updateStatus(blogId, status);
        // 与 BlogServiceImpl.changeStatus 同语义：可见性变化必须清详情缓存
        stringRedisTemplate.delete(BLOG_DETAIL_CACHE_KEY + blogId);
    }

    private void saveRecord(BlogInfo blog, ModerationResult result, String errorCode, String reviewReason) {
        BlogModeration record = new BlogModeration();
        record.setBlogId(blog.getId());
        record.setCreateTime(java.time.LocalDateTime.now());
        if (result != null) {
            record.setVerdict(result.getVerdict());
            record.setConfidence(result.getConfidence());
            record.setReason(result.getReason());
            record.setLayer(result.getLayer());
            record.setLatencyMs(result.getLatencyMs());
        } else {
            record.setErrorCode(errorCode);
            record.setReviewReason(reviewReason);
        }
        moderationMapper.insert(record);
    }

    private ModerationCheckRequest toCheckRequest(BlogInfo blog) {
        return ModerationCheckRequest.builder()
                .blogId(blog.getId())
                .title(blog.getTitle())
                .content(blog.getContent())
                .authorId(blog.getUserId())
                .build();
    }

    private long backoffMs(int attempt, int httpStatus) {
        long base = httpStatus == 429 ? rateLimitBackoffBaseMs : normalBackoffBaseMs;
        long backoff = base * (1L << (attempt - 1)); // 指数：2s, 4s（或 5s, 10s）
        // ±30% jitter：多实例同时重试时错峰，避免重试风暴
        double jitter = 0.7 + ThreadLocalRandom.current().nextDouble() * 0.6;
        return (long) (backoff * jitter);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // 恢复中断标记，让上层决定退出
        }
    }
}
