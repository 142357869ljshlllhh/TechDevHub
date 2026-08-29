package com.techdevhub.dto.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约 DTO 反序列化测试——JSON 样例取自 Python 侧 java_integration_deploy.md 的响应契约。
 * 作用：对端字段变更时这里先红，避免集成期才发现 snake_case 映射漂移。
 */
class AiDtoContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void moderationResult_deserializesPythonContract() throws Exception {
        String json = """
                {"verdict": "reject", "confidence": 0.93,
                 "violations": [{"category": "ad", "snippet": "加V买号", "confidence": 0.95}],
                 "reason": "含广告引流内容", "layer": "llm", "latency_ms": 812}
                """;
        ModerationResult result = mapper.readValue(json, ModerationResult.class);
        assertThat(result.getVerdict()).isEqualTo("reject");
        assertThat(result.getViolations()).hasSize(1);
        assertThat(result.getViolations().get(0).getCategory()).isEqualTo("ad");
        assertThat(result.getLatencyMs()).isEqualTo(812L);
    }

    @Test
    void moderationRequest_serializesSnakeCase() throws Exception {
        ModerationCheckRequest req = ModerationCheckRequest.builder()
                .blogId(1L).title("t").content("c").authorId(9L).build();
        String json = mapper.writeValueAsString(req);
        assertThat(json).contains("\"blog_id\":1").contains("\"author_id\":9");
    }

    @Test
    void chatReply_deserializesPythonContract() throws Exception {
        String json = """
                {"reply": "你好", "conversation_id": "abc12345", "tokens_used": 123}
                """;
        ChatReply reply = mapper.readValue(json, ChatReply.class);
        assertThat(reply.getConversationId()).isEqualTo("abc12345");
        assertThat(reply.getTokensUsed()).isEqualTo(123L);
    }

    @Test
    void qaResponse_deserializesRejectedSemantics() throws Exception {
        String json = """
                {"answer": "社区暂无相关内容", "sources": [], "rejected": true}
                """;
        QAResponse qa = mapper.readValue(json, QAResponse.class);
        assertThat(qa.getRejected()).isTrue();
    }

    @Test
    void agentChatResponse_deserializesPendingAction() throws Exception {
        String json = """
                {"reply": "我准备创建草稿《xx》，请确认后执行。",
                 "conversation_id": "abc12345",
                 "pending_action": {"tool": "create_draft",
                                    "args": {"title": "xx", "content": "yy"},
                                    "summary": "创建草稿《xx》"}}
                """;
        AgentChatResponse resp = mapper.readValue(json, AgentChatResponse.class);
        assertThat(resp.getPendingAction().getTool()).isEqualTo("create_draft");
        assertThat(resp.getPendingAction().getArgs()).containsEntry("title", "xx");
    }

    @Test
    void agentChatRequest_serializesConfirmAction() throws Exception {
        AgentChatRequest req = new AgentChatRequest();
        req.setConversationId("abc12345");
        req.setMessage("");
        req.setConfirmAction(new AgentConfirmAction("create_draft", java.util.Map.of("title", "xx")));
        String json = mapper.writeValueAsString(req);
        assertThat(json).contains("\"confirm_action\"").contains("\"conversation_id\"");
        // 红线：Java 不在 args 里传 user_id（Python 侧会用 X-User-Id 强制覆盖）
        assertThat(json).doesNotContain("user_id");
    }

    @Test
    void errorEnvelope_missingRetryableTreatedAsNotRetryable() throws Exception {
        AiErrorEnvelope envelope = mapper.readValue(
                "{\"detail\": \"bad\", \"code\": \"RAG_PERMANENT\"}", AiErrorEnvelope.class);
        assertThat(envelope.getCode()).isEqualTo("RAG_PERMANENT");
        assertThat(envelope.isRetryableSafe()).isFalse();
    }
}
