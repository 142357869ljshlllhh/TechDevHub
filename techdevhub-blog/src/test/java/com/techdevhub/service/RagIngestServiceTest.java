package com.techdevhub.service;

import com.techdevhub.client.AiServiceClient;
import com.techdevhub.dto.ai.AiResult;
import com.techdevhub.dto.ai.RagIngestResult;
import com.techdevhub.entity.BlogInfo;
import com.techdevhub.exception.AiCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T6 语义：ingest 失败仅告警，绝不上抛阻断发布链路。
 * （原用例在 ModerationFlowServiceTest，ingest 拆独立 Bean 后随迁。）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RagIngestServiceTest {

    @Mock
    private AiServiceClient aiServiceClient;

    private RagIngestService service;

    @BeforeEach
    void setUp() {
        service = new RagIngestService(aiServiceClient);
    }

    private static BlogInfo blog() {
        BlogInfo b = new BlogInfo();
        b.setId(100L);
        b.setUserId(9L);
        b.setTitle("t");
        b.setContent("c");
        return b;
    }

    @Test
    void success_callsClient() {
        when(aiServiceClient.ingest(any()))
                .thenReturn(new AiResult<>(200, "success", new RagIngestResult(100L, 3)));

        service.ingest(blog());

        verify(aiServiceClient).ingest(any());
    }

    @Test
    void ingestFailure_neverThrowsAndDoesNotBlock() {
        when(aiServiceClient.ingest(any()))
                .thenThrow(new AiCallException("RAG 故障", 503, "RAG_TEMPORARY", true));

        service.ingest(blog()); // 不应抛出

        verify(aiServiceClient).ingest(any());
    }
}
