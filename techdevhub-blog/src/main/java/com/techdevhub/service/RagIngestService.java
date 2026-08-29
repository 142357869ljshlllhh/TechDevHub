package com.techdevhub.service;

import com.techdevhub.client.AiServiceClient;
import com.techdevhub.dto.ai.AiResult;
import com.techdevhub.dto.ai.RagIngestRequest;
import com.techdevhub.dto.ai.RagIngestResult;
import com.techdevhub.entity.BlogInfo;
import com.techdevhub.exception.AiCallException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * RAG 知识库摄取旁路。
 * 为什么独立成 Bean：ingest 标 @Async，但曾被同类 ModerationFlowService.applyVerdict
 * 内部 this.ingest() 自调用——Spring AOP 代理下自调用不经过代理，@Async 静默失效，
 * 管理端批量重审 50 篇会在 HTTP 请求线程里逐篇同步做 30s 级摄取直到超时。
 * 跨 Bean 调用才走代理，所以必须拆出去。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestService {

    private final AiServiceClient aiServiceClient;

    /**
     * 异步摄取一篇博客进知识库。
     * 幂等（同 blog_id 重摄=覆盖）→ 失败仅告警不阻塞发布，补偿靠管理端重发或下次更新。
     */
    @Async("asyncExecutor")
    public void ingest(BlogInfo blog) {
        try {
            AiResult<RagIngestResult> resp = aiServiceClient.ingest(
                    new RagIngestRequest(blog.getId(), blog.getTitle(), blog.getContent()));
            log.info("RAG ingest 完成 blogId={} chunks={}",
                    blog.getId(), resp.getData() == null ? -1 : resp.getData().getChunkCount());
        } catch (AiCallException e) {
            // 只告警不打断：ingest 失败不影响用户已发布成功的事实
            log.warn("RAG ingest 失败（可补偿重发）blogId={} code={}", blog.getId(), e.getAiCode(), e);
        }
    }
}
