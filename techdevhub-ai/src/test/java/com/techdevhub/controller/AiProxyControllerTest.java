package com.techdevhub.controller;

import com.techdevhub.client.PythonAiClient;
import com.techdevhub.dto.ai.AgentChatRequest;
import com.techdevhub.dto.ai.AgentPendingAction;
import com.techdevhub.dto.ai.AgentChatResponse;
import com.techdevhub.dto.ai.ChatReply;
import com.techdevhub.dto.ai.ChatRequest;
import com.techdevhub.dto.ai.QAResponse;
import com.techdevhub.exception.AiCallException;
import com.techdevhub.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T3 DoD：前端代理端点契约测试（standalone MockMvc + mock 客户端，不依赖真实 Python）。
 * 覆盖：RAG rejected 透传、X-User-Id 身份校验、AI 错误 → 503 映射、SSE 帧序与 error 帧。
 */
class AiProxyControllerTest {

    private PythonAiClient client;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(PythonAiClient.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiProxyController(client))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void qa_rejectedTruePassedThroughAsNormalResult() throws Exception {
        Mockito.when(client.ragQuery(any()))
                .thenReturn(new QAResponse("社区暂无相关内容", List.of(), true));
        mockMvc.perform(post("/ai/qa")
                        .contentType("application/json")
                        .content("{\"query\":\"量子计算入门\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 关键语义：rejected=true 是正常业务结果，绝不重试、原样透传
                .andExpect(jsonPath("$.data.rejected").value(true));
    }

    @Test
    void chat_requiresUserId() throws Exception {
        mockMvc.perform(post("/ai/v2/chat")
                        .contentType("application/json")
                        .content("{\"conversation_id\":\"conv12345\",\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(405)); // UNAUTHORIZED 数字码（拦截器同款）
    }

    @Test
    void chat_passesUserIdFromJwtAttribute() throws Exception {
        Mockito.when(client.chat(any(), eq(42L)))
                .thenReturn(new ChatReply("hi", "conv12345", 10L));
        mockMvc.perform(post("/ai/v2/chat")
                        .requestAttr("currentUserId", 42L)
                        .contentType("application/json")
                        .content("{\"conversation_id\":\"conv12345\",\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("hi"));
    }

    @Test
    void temporaryAiError_mapsTo503() throws Exception {
        Mockito.when(client.chat(any(), eq(42L)))
                .thenThrow(new AiCallException("AI 服务暂时不可用", 503, "CHAT_LLM_TEMPORARY", true));
        mockMvc.perform(post("/ai/v2/chat")
                        .requestAttr("currentUserId", 42L)
                        .contentType("application/json")
                        .content("{\"conversation_id\":\"conv12345\",\"message\":\"hi\"}"))
                .andExpect(status().is(503))
                .andExpect(jsonPath("$.code").value(1600)); // AI_SERVICE_TEMPORARY
    }

    @Test
    void agentChat_returnsPendingActionForConfirmation() throws Exception {
        Mockito.when(client.agentChat(any(), eq(42L)))
                .thenReturn(new AgentChatResponse("我准备创建草稿《xx》，请确认后执行。", "conv12345",
                        new AgentPendingAction("create_draft",
                                java.util.Map.of("title", "xx"), "创建草稿《xx》")));
        mockMvc.perform(post("/ai/assistant/chat")
                        .requestAttr("currentUserId", 42L)
                        .contentType("application/json")
                        .content("{\"conversation_id\":\"conv12345\",\"message\":\"帮我建个草稿\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending_action.tool").value("create_draft"))
                .andExpect(jsonPath("$.data.pending_action.summary").value("创建草稿《xx》"));
    }

    @Test
    void chatStream_forwardsFramesInOrderAndCompletesOnDone() throws Exception {
        Mockito.when(client.chatStream(any(), eq(42L)))
                .thenReturn(FluxJust.events());

        MvcResult async = mockMvc.perform(post("/ai/v2/chat/stream")
                        .requestAttr("currentUserId", 42L)
                        .contentType("application/json")
                        .content("{\"conversation_id\":\"conv12345\",\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // 帧序契约：ping → delta → [DONE]，data 内容原样转发
        assertThat(body).contains("{\"type\": \"ping\"}");
        assertThat(body).contains("\"delta\"");
        assertThat(body).contains("[DONE]");
    }

    @Test
    void chatStream_preStreamError_becomesReadableErrorFrame() throws Exception {
        Mockito.when(client.agentChatStream(any(), eq(42L)))
                .thenReturn(reactor.core.publisher.Flux.error(
                        new AiCallException("AI 服务暂时不可用，请稍后重试", 503, "AGENT_TEMPORARY", true)));

        MvcResult async = mockMvc.perform(post("/ai/assistant/chat/stream")
                        .requestAttr("currentUserId", 42L)
                        .contentType("application/json")
                        .content("{\"conversation_id\":\"conv12345\",\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // 流建立前故障补发标准 error 帧，前端与流内错误同一套解析
        assertThat(body).contains("\"type\":\"error\"").contains("AI 服务暂时不可用");
    }

    /** 工具方法：SSE 三帧静态流 */
    private static final class FluxJust {
        private static reactor.core.publisher.Flux<ServerSentEvent<String>> events() {
            return reactor.core.publisher.Flux.fromIterable(List.of(
                    ServerSentEvent.<String>builder().data("{\"type\": \"ping\"}").build(),
                    ServerSentEvent.<String>builder().data("{\"type\": \"delta\", \"content\": \"你\"}").build(),
                    ServerSentEvent.<String>builder().data("[DONE]").build()));
        }
    }
}
