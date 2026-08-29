package com.techdevhub.service;

import com.techdevhub.client.AiServiceClient;
import com.techdevhub.dto.ai.AiResult;
import com.techdevhub.dto.ai.ModerationResult;
import com.techdevhub.entity.BlogInfo;
import com.techdevhub.entity.BlogModeration;
import com.techdevhub.exception.AiCallException;
import com.techdevhub.mapper.BlogMapper;
import com.techdevhub.mapper.BlogModerationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T5 DoD：审核状态机五路径单测（fake 客户端注入，无外部依赖）。
 * approve→发布 / review→人工 / reject→下架 / 可重试耗尽→GIVEUP / 不可重试→直接 GIVEUP。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationFlowServiceTest {

    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private BlogMapper blogMapper;
    @Mock
    private BlogModerationMapper moderationMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private RagIngestService ragIngestService;

    private ModerationFlowService service;

    private static BlogInfo blog() {
        BlogInfo b = new BlogInfo();
        b.setId(100L);
        b.setUserId(9L);
        b.setTitle("t");
        b.setContent("c");
        return b;
    }

    @BeforeEach
    void setUp() {
        service = new ModerationFlowService(aiServiceClient, blogMapper, moderationMapper,
                stringRedisTemplate, ragIngestService);
        // 单测不真实 sleep；真实基数 2s/5s 在字段注释里说明
        service.setNormalBackoffBaseMs(0);
        service.setRateLimitBackoffBaseMs(0);
    }

    private static ModerationResult verdict(String v) {
        return new ModerationResult(v, 0.9, java.util.List.of(), "r", "llm", 100L);
    }

    @Test
    void approve_publishesAndTriggersIngest() {
        when(aiServiceClient.check(any())).thenReturn(new AiResult<>(200, "success", verdict("approve")));

        service.moderate(blog());

        verify(blogMapper).updateStatus(100L, 1);
        ArgumentCaptor<BlogModeration> captor = ArgumentCaptor.forClass(BlogModeration.class);
        verify(moderationMapper).insert(captor.capture());
        assertThat(captor.getValue().getVerdict()).isEqualTo("approve");
        // 审核通过 → 经独立 RagIngestService 异步进知识库（跨 Bean 才走 @Async 代理）
        verify(ragIngestService).ingest(any(BlogInfo.class));
    }

    @Test
    void review_staysUnderReview() {
        when(aiServiceClient.check(any())).thenReturn(new AiResult<>(200, "success", verdict("review")));

        service.moderate(blog());

        // review 不改状态：停在 0（审核中），进人工队列
        verify(blogMapper, never()).updateStatus(anyLong(), anyInt());
        verify(ragIngestService, never()).ingest(any());
        ArgumentCaptor<BlogModeration> captor = ArgumentCaptor.forClass(BlogModeration.class);
        verify(moderationMapper).insert(captor.capture());
        assertThat(captor.getValue().getVerdict()).isEqualTo("review");
    }

    @Test
    void reject_takesDownAndRecords() {
        when(aiServiceClient.check(any())).thenReturn(new AiResult<>(200, "success", verdict("reject")));

        service.moderate(blog());

        verify(blogMapper).updateStatus(100L, 2);
        verify(ragIngestService, never()).ingest(any());
    }

    @Test
    void retryableExhausted_givesUpWithAgentFailure() {
        when(aiServiceClient.check(any()))
                .thenThrow(new AiCallException("LLM 故障", 503, "MODERATION_LLM_TEMPORARY", true));

        service.moderate(blog());

        // 状态机：3 次尝试后 GIVEUP，status 保持 0 不动，挂 AGENT_FAILURE 等人工重审
        verify(aiServiceClient, times(3)).check(any());
        verify(blogMapper, never()).updateStatus(anyLong(), anyInt());
        ArgumentCaptor<BlogModeration> captor = ArgumentCaptor.forClass(BlogModeration.class);
        verify(moderationMapper).insert(captor.capture());
        assertThat(captor.getValue().getReviewReason()).isEqualTo("AGENT_FAILURE");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("MODERATION_LLM_TEMPORARY");
    }

    @Test
    void nonRetryable_givesUpImmediately() {
        when(aiServiceClient.check(any()))
                .thenThrow(new AiCallException("负载不合法", 502, "MODERATION_LLM_PERMANENT", false));

        service.moderate(blog());

        // retryable=false：重试只会浪费额度，1 次即止
        verify(aiServiceClient, times(1)).check(any());
        verify(blogMapper, never()).updateStatus(anyLong(), anyInt());
        verify(moderationMapper).insert(any(BlogModeration.class));
    }
}
