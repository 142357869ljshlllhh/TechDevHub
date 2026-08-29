package com.techdevhub.client;

import com.techdevhub.dto.ai.AiResult;
import com.techdevhub.dto.ai.ModerationCheckRequest;
import com.techdevhub.dto.ai.ModerationResult;
import com.techdevhub.dto.ai.QAResponse;
import com.techdevhub.dto.ai.RagDeleteRequest;
import com.techdevhub.dto.ai.RagDeleteResponse;
import com.techdevhub.dto.ai.RagIngestRequest;
import com.techdevhub.dto.ai.RagIngestResult;
import com.techdevhub.dto.ai.RagQueryRequest;
import com.techdevhub.dto.ai.RecheckRequest;
import com.techdevhub.dto.ai.RecheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * ai-service 内部转发端点客户端（服务间，X-Internal-Token 门禁）。
 * 为什么经 ai-service 而不直连 Python：Python 契约只在 ai-service 感知，
 * blog 只认 Java 风格接口；错误语义通过数字码（1600/1601/1602）无损还原 retryable。
 *
 * 与 UserProfileClient 同款 url 直连风格（绕过网关少一跳，内部端点无需路由）。
 */
@FeignClient(name = "ai-service",
        url = "${techdevhub.feign.ai-service-url:http://localhost:8088}",
        path = "/ai/internal",
        configuration = AiFeignConfig.class)
public interface AiServiceClient {

    @PostMapping("/moderation/check")
    AiResult<ModerationResult> check(@RequestBody ModerationCheckRequest request);

    @PostMapping("/moderation/recheck")
    AiResult<RecheckResponse> recheck(@RequestBody RecheckRequest request);

    @PostMapping("/rag/ingest")
    AiResult<RagIngestResult> ingest(@RequestBody RagIngestRequest request);

    @PostMapping("/rag/query")
    AiResult<QAResponse> query(@RequestBody RagQueryRequest request);

    @PostMapping("/rag/delete")
    AiResult<RagDeleteResponse> ragDelete(@RequestBody RagDeleteRequest request);
}
