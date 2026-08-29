package com.techdevhub.service;

import com.techdevhub.client.AiServiceClient;
import com.techdevhub.dto.ai.AiResult;
import com.techdevhub.dto.ai.RagDeleteRequest;
import com.techdevhub.dto.ai.RagDeleteResponse;
import com.techdevhub.dto.ai.RagIngestRequest;
import com.techdevhub.dto.ai.RagIngestResult;
import com.techdevhub.entity.BlogInfo;
import com.techdevhub.exception.AiCallException;
import com.techdevhub.mapper.RagIndexStatusMapper;
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
    private final RagIndexStatusMapper ragIndexStatusMapper;

    /**
     * 异步摄取一篇博客进知识库。
     * 幂等（同 blog_id 重摄=覆盖，整篇替换语义）→ 失败不阻塞发布，
     * 但状态留痕 rag_index_status（管理端可见可重试，M10 生命周期闭环）。
     */
    @Async("asyncExecutor")
    public void ingest(BlogInfo blog) {
        markStatus(blog.getId(), "pending", null);
        try {
            AiResult<RagIngestResult> resp = aiServiceClient.ingest(
                    new RagIngestRequest(blog.getId(), blog.getTitle(), blog.getContent()));
            markStatus(blog.getId(), "ok", null);
            log.info("RAG ingest 完成 blogId={} chunks={}",
                    blog.getId(), resp.getData() == null ? -1 : resp.getData().getChunkCount());
        } catch (AiCallException e) {
            // 只告警不打断：ingest 失败不影响用户已发布成功的事实，但状态可查可补偿
            markStatus(blog.getId(), "failed", e.getAiCode());
            log.warn("RAG ingest 失败（管理端可重试）blogId={} code={}", blog.getId(), e.getAiCode(), e);
        }
    }

    /**
     * 异步移除：删除/下架/驳回/编辑重审前的整篇出库。
     * 成功置 removed；失败置 failed（告警 + 状态可见，避免"删了文章 RAG 还在引用"）。
     */
    @Async("asyncExecutor")
    public void deleteAsync(Long blogId) {
        try {
            RagDeleteRequest req = new RagDeleteRequest();
            req.setBlogId(blogId);
            AiResult<RagDeleteResponse> resp = aiServiceClient.ragDelete(req);
            markStatus(blogId, "removed", null);
            log.info("RAG 移除完成 blogId={} chunks={}",
                    blogId, resp.getData() == null || resp.getData().getDeletedChunks() == null
                            ? -1 : resp.getData().getDeletedChunks());
        } catch (AiCallException e) {
            markStatus(blogId, "failed", e.getAiCode());
            log.warn("RAG 移除失败 blogId={} code={}", blogId, e.getAiCode(), e);
        }
    }

    /** 状态留痕（管理端可见性数据源）；写失败仅告警，不反向影响主流程 */
    private void markStatus(Long blogId, String status, String errorCode) {
        try {
            ragIndexStatusMapper.upsert(blogId, status, errorCode);
        } catch (Exception ex) {
            log.warn("RAG 状态写入失败 blogId={} status={}", blogId, status, ex);
        }
    }
}
