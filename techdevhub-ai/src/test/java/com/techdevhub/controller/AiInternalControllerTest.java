package com.techdevhub.controller;

import com.techdevhub.client.PythonAiClient;
import com.techdevhub.config.AiPythonProperties;
import com.techdevhub.dto.ai.ModerationCheckRequest;
import com.techdevhub.dto.ai.ModerationResult;
import com.techdevhub.dto.ai.QAResponse;
import com.techdevhub.dto.ai.RagQueryRequest;
import com.techdevhub.exception.AiCallException;
import com.techdevhub.exception.GlobalExceptionHandler;
import com.techdevhub.filter.InternalTokenFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T4 DoD：内部转发端点契约测试。
 * 覆盖：门禁（缺 token 401 / 错 token 403 / 留空放行）、转发与结果包装、错误语义透传。
 */
class AiInternalControllerTest {

    private PythonAiClient client;
    private MockMvc mockMvcWithToken;
    private MockMvc mockMvcWithoutToken;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(PythonAiClient.class);
        AiInternalController controller = new AiInternalController(client);

        AiPythonProperties secured = new AiPythonProperties();
        secured.setInternalToken("secret-token");
        InternalTokenFilter securedFilter = new InternalTokenFilter(secured, new ObjectMapper());

        AiPythonProperties open = new AiPythonProperties(); // token 留空=本地联调
        InternalTokenFilter openFilter = new InternalTokenFilter(open, new ObjectMapper());

        mockMvcWithToken = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(securedFilter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mockMvcWithoutToken = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(openFilter)
                .build();
    }

    private static final String CHECK_BODY = """
            {"blog_id":1,"title":"t","content":"c","author_id":9}
            """;

    @Test
    void missingToken_rejected401() throws Exception {
        mockMvcWithToken.perform(post("/ai/internal/moderation/check")
                        .contentType("application/json").content(CHECK_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1604));
    }

    @Test
    void wrongToken_rejected403() throws Exception {
        mockMvcWithToken.perform(post("/ai/internal/moderation/check")
                        .header("X-Internal-Token", "bad")
                        .contentType("application/json").content(CHECK_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyTokenConfig_allowsLocalDebug() throws Exception {
        Mockito.when(client.moderationCheck(any())).thenReturn(
                new ModerationResult("approve", 0.93, java.util.List.of(), "ok", "rule", 10L));
        mockMvcWithoutToken.perform(post("/ai/internal/moderation/check")
                        .contentType("application/json").content(CHECK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict").value("approve"));
    }

    @Test
    void check_withValidToken_forwardsAndWraps() throws Exception {
        Mockito.when(client.moderationCheck(any())).thenReturn(
                new ModerationResult("review", 0.5, java.util.List.of(), "拿不准", "llm", 900L));
        mockMvcWithToken.perform(post("/ai/internal/moderation/check")
                        .header("X-Internal-Token", "secret-token")
                        .contentType("application/json").content(CHECK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict").value("review"))
                .andExpect(jsonPath("$.data.layer").value("llm"));
    }

    @Test
    void temporaryError_propagates503WithRetryableCode() throws Exception {
        // blog 侧 ErrorDecoder 凭 503+1600 还原 retryable=true，重试语义不丢
        Mockito.when(client.ragQuery(any()))
                .thenThrow(new AiCallException("RAG 故障", 503, "RAG_TEMPORARY", true));
        mockMvcWithToken.perform(post("/ai/internal/rag/query")
                        .header("X-Internal-Token", "secret-token")
                        .contentType("application/json").content("{\"query\":\"q\"}"))
                .andExpect(status().is(503))
                .andExpect(jsonPath("$.code").value(1600))
                .andExpect(jsonPath("$.message").value("RAG 故障 [RAG_TEMPORARY]"));
    }

    @Test
    void qa_rejectedTruePassedThroughInsideResult() throws Exception {
        Mockito.when(client.ragQuery(any()))
                .thenReturn(new QAResponse("无覆盖", java.util.List.of(), true));
        mockMvcWithToken.perform(post("/ai/internal/rag/query")
                        .header("X-Internal-Token", "secret-token")
                        .contentType("application/json").content("{\"query\":\"q\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rejected").value(true));
    }
}
