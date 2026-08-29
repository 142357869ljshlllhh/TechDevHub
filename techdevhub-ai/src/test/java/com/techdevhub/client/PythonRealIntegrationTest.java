package com.techdevhub.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdevhub.config.AiPythonProperties;
import com.techdevhub.dto.ai.AgentChatRequest;
import com.techdevhub.dto.ai.AgentChatResponse;
import com.techdevhub.dto.ai.ChatReply;
import com.techdevhub.dto.ai.ChatRequest;
import com.techdevhub.dto.ai.ModerationCheckRequest;
import com.techdevhub.dto.ai.ModerationResult;
import com.techdevhub.dto.ai.QAResponse;
import com.techdevhub.dto.ai.RagIngestRequest;
import com.techdevhub.dto.ai.RagIngestResult;
import com.techdevhub.dto.ai.RagQueryRequest;
import com.techdevhub.dto.ai.RecheckRequest;
import com.techdevhub.dto.ai.RecheckResponse;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真连 Python 服务的联调测试（T9）——MockWebServer 测试验证不了
 * "真进程的字段序列化/头名/路径"三方对齐，本类补上这层。
 *
 * 为什么默认 skip：依赖 localhost:8000 有活的 Python 服务（fake 模式即可），
 * CI 与离线开发不该因此挂红。启用方式：
 * <pre>
 *   # 终端1：cd techdevhub-agent && .venv/Scripts/python -m uvicorn app.main:app --port 8000
 *   # 终端2：set AI_REAL_TEST=true && mvn -pl techdevhub-ai test -Dtest=PythonRealIntegrationTest
 * </pre>
 * 为什么不起 @SpringBootTest：本类只验证客户端契约，PythonAiClient 无 Spring
 * 依赖可纯构造——绕开 Nacos/MySQL 拉起整个上下文的重量。
 */
@EnabledIfEnvironmentVariable(named = "AI_REAL_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PythonRealIntegrationTest {

    private final PythonAiClient client = new PythonAiClient(
            new AiPythonProperties(), new ObjectMapper());
    private final long userId = 42L;

    @Test
    @Order(1)
    void moderationCheckReturnsVerdict() {
        ModerationResult result = client.moderationCheck(new ModerationCheckRequest(
                990001L, "JVM 内存模型详解",
                "本文讲解 JVM 运行时数据区的职责划分与 GC 日志分析方法。", 42L));
        assertNotNull(result.getVerdict());
        assertTrue(List.of("approve", "review", "reject").contains(result.getVerdict()),
                "verdict 必须是三态之一: " + result.getVerdict());
        assertTrue(List.of("rule", "llm").contains(result.getLayer()),
                "layer 必须是 rule|llm: " + result.getLayer());
        assertNotNull(result.getLatencyMs(), "latency_ms 字段必须被 snake_case 映射到");
    }

    @Test
    @Order(2)
    void chatRoundtripWithMemory() {
        String cid = "it-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ChatReply reply = client.chat(new ChatRequest(cid, "你好"), userId);
        assertNotNull(reply.getReply());
        assertEquals(cid, reply.getConversationId(), "会话 ID 必须回显（conversation_id 映射）");
    }

    @Test
    @Order(3)
    void ragIngestThenQueryAnswers() {
        client.ragIngest(new RagIngestRequest(990002L, "Spring 事务失效",
                "Spring 事务失效最常见的原因是自调用：同类方法 A 调用方法 B 走 this 引用而非代理对象，切面被绕过。"));
        // fake embedding 无语义：只要管道通（rejected=false 且有 sources）即算联调通过
        QAResponse qa = client.ragQuery(new RagQueryRequest("Spring 事务为什么会失效"));
        assertNotNull(qa.getAnswer());
        if (!Boolean.TRUE.equals(qa.getRejected())) {
            assertFalse(qa.getSources().isEmpty(), "非拒答必须带 sources（引用一致性契约）");
        }
        // rejected=true 在 fake 模式下也是合法结果（低分诚实拒答），两种都算通过
    }

    @Test
    @Order(4)
    void sseFrameSequenceEndsWithDone() {
        String cid = "it-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Flux<ServerSentEvent<String>> flux = client.chatStream(new ChatRequest(cid, "讲讲 Redis"), userId);
        List<ServerSentEvent<String>> frames = flux.take(20).collectList().block(Duration.ofSeconds(30));
        assertNotNull(frames);
        assertFalse(frames.isEmpty());
        assertTrue(frames.stream().anyMatch(f -> f.data() != null && f.data().contains("ping")),
                "首段必须有 ping 心跳帧");
        assertTrue(frames.stream().anyMatch(f -> f.data() != null && f.data().contains("delta")),
                "必须有 delta 增量帧");
        assertTrue(frames.stream().anyMatch(f -> "[DONE]".equals(f.data())),
                "必须以 [DONE] 哨兵收尾");
    }

    @Test
    @Order(5)
    void agentChatSmoke() {
        String cid = "it-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        AgentChatResponse resp = client.agentChat(
                new AgentChatRequest(cid, "社区里有什么好文章", null), userId);
        assertNotNull(resp.getReply());
        assertEquals(cid, resp.getConversationId());
        // fake 模型不发起工具调用，pendingAction 应为 null；真模型联调时可能非空（两跳协议）
    }

    @Test
    @Order(6)
    void recheckBatchRoundtrip() {
        RecheckResponse resp = client.moderationRecheck(new RecheckRequest(List.of(
                new ModerationCheckRequest(990003L, "重审标题", "重审正文内容，验证 recheck 契约。", 42L))));
        assertNotNull(resp.getResults());
        assertEquals(1, resp.getResults().size(), "results 与 items 顺序一一对应");
    }
}
