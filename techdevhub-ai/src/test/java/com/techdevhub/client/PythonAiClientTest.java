package com.techdevhub.client;

import com.techdevhub.config.AiPythonProperties;
import com.techdevhub.context.UserContext;
import com.techdevhub.dto.ai.ChatReply;
import com.techdevhub.dto.ai.ChatRequest;
import com.techdevhub.dto.ai.ModerationCheckRequest;
import com.techdevhub.dto.ai.ModerationResult;
import com.techdevhub.exception.AiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.codec.EncodingException;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PythonAiClient 契约测试（T2 DoD）：
 * 用 MockWebServer 模拟 Python 端，验证三路径——
 * 200 领域对象直反序列化 / 503 错误信封翻译 / 429 限流语义，以及请求头注入。
 * 为什么不连真实 Python：单测要 hermetic，CI 无外部依赖。
 */
class PythonAiClientTest {

    private MockWebServer server;
    private PythonAiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        AiPythonProperties props = new AiPythonProperties();
        props.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
        props.setInternalToken("itoken-xyz");
        props.setAdminToken("atoken-xyz");
        client = new PythonAiClient(props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private void enqueueJson(int status, String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    @Test
    void moderationCheck_success_deserializesDomainObject() throws Exception {
        enqueueJson(200, """
                {"verdict":"approve","confidence":0.93,"violations":[],
                 "reason":"ok","layer":"rule","latency_ms":42}
                """);
        ModerationResult result = client.moderationCheck(ModerationCheckRequest.builder()
                .blogId(1L).title("t").content("c").authorId(9L).build());

        assertThat(result.getVerdict()).isEqualTo("approve");
        assertThat(result.getLatencyMs()).isEqualTo(42L);

        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded.getPath()).isEqualTo("/api/v1/moderation/check");
        String sent = recorded.getBody().readUtf8();
        assertThat(sent).contains("\"blog_id\":1").contains("\"author_id\":9");
        // 门禁头必须注入，否则生产环境会被 Python 侧 401 拒绝
        assertThat(recorded.getHeader("X-Internal-Token")).isEqualTo("itoken-xyz");
    }

    @Test
    void chat_injectsUserIdAndTraceId() throws Exception {
        enqueueJson(200, """
                {"reply":"hi","conversation_id":"conv12345","tokens_used":10}
                """);
        UserContext.setTraceId("trace-001");
        try {
            ChatReply reply = client.chat(new ChatRequest("conv12345", "你好"), 42L);
            assertThat(reply.getReply()).isEqualTo("hi");
            RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(recorded.getPath()).isEqualTo("/api/v1/chat");
            assertThat(recorded.getHeader("X-User-Id")).isEqualTo("42");
            assertThat(recorded.getHeader("X-Trace-Id")).isEqualTo("trace-001");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void temporaryError_throwsAiCallExceptionWithRetryable() {
        // 与 Python 契约一致：503 + 两段式错误码 + retryable=true → 状态机应走退避重试
        enqueueJson(503, """
                {"detail":"LLM 调用失败","code":"MODERATION_LLM_TEMPORARY","retryable":true}
                """);
        assertThatThrownBy(() -> client.moderationCheck(ModerationCheckRequest.builder()
                        .blogId(1L).title("t").content("c").authorId(9L).build()))
                .isInstanceOfSatisfying(AiCallException.class, e -> {
                    assertThat(e.getAiCode()).isEqualTo("MODERATION_LLM_TEMPORARY");
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.getHttpStatus()).isEqualTo(503);
                });
    }

    @Test
    void rateLimited_keepsRetryableTrueForStateMachine() {
        enqueueJson(429, """
                {"detail":"rate limited","code":"RATE_LIMITED","retryable":true}
                """);
        assertThatThrownBy(() -> client.ragQuery(new com.techdevhub.dto.ai.RagQueryRequest("q")))
                .isInstanceOfSatisfying(AiCallException.class,
                        e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    void permanentError_nonRetryableEnvelope() {
        enqueueJson(422, """
                {"detail":"内容为空","code":"RAG_CONTENT_EMPTY","retryable":false}
                """);
        assertThatThrownBy(() -> client.ragIngest(
                        new com.techdevhub.dto.ai.RagIngestRequest(1L, "t", "")))
                .isInstanceOfSatisfying(AiCallException.class,
                        e -> assertThat(e.isRetryable()).isFalse());
    }

    @Test
    void connectionRefused_translatesToTransportException() {
        // 指向一个必然没人监听的端口，验证网络层故障 → retryable=true 的临时故障
        AiPythonProperties props = new AiPythonProperties();
        props.setBaseUrl("http://127.0.0.1:59999");
        PythonAiClient deadClient = new PythonAiClient(props, new ObjectMapper());
        assertThatThrownBy(() -> deadClient.chat(new ChatRequest("conv12345", "hi"), 1L))
                .isInstanceOfSatisfying(AiCallException.class, e -> {
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.getAiCode()).isNull();
                });
    }

    @Test
    void chatStream_emitsRawSseFrames() throws Exception {
        // SSE 契约：ping → delta → [DONE]，Java 侧只收不解析，透传层原样转发
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\": \"ping\"}\n\n"
                        + "data: {\"type\": \"delta\", \"content\": \"你好\"}\n\n"
                        + "data: [DONE]\n\n"));
        Flux<ServerSentEvent<String>> flux = client.chatStream(
                new ChatRequest("conv12345", "hi"), 42L);

        List<ServerSentEvent<String>> events = flux.collectList().block(java.time.Duration.ofSeconds(5));
        assertThat(events).isNotNull();
        assertThat(events).hasSize(3);
        assertThat(events.get(0).data()).isEqualTo("{\"type\": \"ping\"}");
        assertThat(events.get(1).data()).contains("\"delta\"");
        assertThat(events.get(2).data()).isEqualTo("[DONE]");

        // 请求体断言（联调期 422 "Field required" 的定位用）：流式路径与同步路径
        // 唯一差异是 accept 头与客户端实例——body 序列化必须完全一致，
        // 这里若缺失 conversation_id 即锁死 WebClient 流式分支的编码问题
        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded.getPath()).isEqualTo("/api/v1/chat/stream");
        assertThat(recorded.getHeader("Accept")).contains("text/event-stream");
        assertThat(recorded.getHeader("X-User-Id")).isEqualTo("42");
        assertThat(recorded.getBody().readUtf8())
                .contains("\"conversation_id\"").contains("conv12345")
                .contains("\"message\"");
    }
}
