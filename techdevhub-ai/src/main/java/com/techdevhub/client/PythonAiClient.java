package com.techdevhub.client;

import com.techdevhub.config.AiPythonProperties;
import com.techdevhub.context.UserContext;
import com.techdevhub.dto.ai.AgentChatRequest;
import com.techdevhub.dto.ai.AgentChatResponse;
import com.techdevhub.dto.ai.AgentHistoryResponse;
import com.techdevhub.dto.ai.AiErrorEnvelope;
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
import com.techdevhub.exception.AiCallException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Python AI 微服务唯一客户端（ai-service 是全系统唯一直连 Python 的 Java 模块）。
 *
 * 设计要点：
 * 1. 双 WebClient 实例——同步调用 read timeout 30s（LLM 生成慢），流式调用不设整体
 *    超时（长回答），但配 120s 读空闲兜底（心跳 15s，静默 120s 即判定半开连接）。为什么不能共用：超时参数长在 HttpClient 上，无法按请求覆盖。
 * 2. 失败统一翻译：非 2xx 读 AiErrorEnvelope 信封 → AiCallException；
 *    网络层故障（连不上/超时）→ AiCallException.transport（retryable=true）。
 * 3. 请求头在此统一注入，调用方不再感知门禁细节。
 */
@Slf4j
@Component
public class PythonAiClient {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING =
            new ParameterizedTypeReference<>() { };

    private final WebClient syncClient;
    private final WebClient streamClient;
    private final AiPythonProperties props;
    private final ObjectMapper objectMapper;

    public PythonAiClient(AiPythonProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        // 连接超时 5s：Python 服务挂掉时快速失败，不占着业务线程
        HttpClient syncHttp = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30));
        // 流式不设整体 responseTimeout（长回答），但 120s 读空闲兜底：
        // 心跳 15s，正常流远不会静默 120s——半开连接/心跳停发时强制断开，
        // 防止 Servlet 异步上下文 + netty 连接被无限期占用（2C2G 小堆下是致命的）
        HttpClient streamHttp = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(120)));
        this.syncClient = build(syncHttp);
        this.streamClient = build(streamHttp);
    }

    private WebClient build(HttpClient httpClient) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    // ---------- 同步端点（系统级：审核 / RAG 摄取，无用户身份头） ----------

    public ModerationResult moderationCheck(ModerationCheckRequest request) {
        return post("/api/v1/moderation/check", request, ModerationResult.class, null, false);
    }

    public RecheckResponse moderationRecheck(RecheckRequest request) {
        // recheck 需要 X-Admin-Token 门禁
        return post("/api/v1/moderation/recheck", request, RecheckResponse.class, null, true);
    }

    public RagIngestResult ragIngest(RagIngestRequest request) {
        return post("/api/v1/rag/ingest", request, RagIngestResult.class, null, false);
    }

    // ---------- 同步端点（人类级：需要 X-User-Id） ----------

    public QAResponse ragQuery(RagQueryRequest request) {
        return post("/api/v1/rag/query", request, QAResponse.class, null, false);
    }

    public ChatReply chat(ChatRequest request, Long userId) {
        return post("/api/v1/chat", request, ChatReply.class, userId, false);
    }

    public AgentChatResponse agentChat(AgentChatRequest request, Long userId) {
        return post("/api/v1/agent/chat", request, AgentChatResponse.class, userId, false);
    }

    /**
     * 会话历史回放（GET，前端刷新后恢复消息列表）。
     * 403（会话不属于该用户）经信封翻译成 AiCallException(retryable=false)，由代理层透传前端。
     */
    public AgentHistoryResponse agentHistory(String conversationId, Long userId) {
        try {
            return syncClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/agent/history")
                            .queryParam("conversation_id", conversationId)
                            .build())
                    .headers(h -> injectHeaders(h, userId, false))
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            resp -> resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(raw -> toAiCallException(resp.statusCode().value(), raw)))
                    .bodyToMono(AgentHistoryResponse.class)
                    .block(Duration.ofSeconds(15));
        } catch (AiCallException e) {
            throw e;
        } catch (Exception e) {
            throw translateTransport(e);
        }
    }

    // ---------- 流式端点（返回原始 SSE 帧流，透传层负责转发） ----------

    public Flux<ServerSentEvent<String>> chatStream(ChatRequest request, Long userId) {
        return stream("/api/v1/chat/stream", request, userId);
    }

    public Flux<ServerSentEvent<String>> agentChatStream(AgentChatRequest request, Long userId) {
        return stream("/api/v1/agent/chat/stream", request, userId);
    }

    // ---------- 内部实现 ----------

    private <T> T post(String path, Object body, Class<T> type, Long userId, boolean admin) {
        try {
            return syncClient.post()
                    .uri(path)
                    .headers(h -> injectHeaders(h, userId, admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            resp -> resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(raw -> toAiCallException(resp.statusCode().value(), raw)))
                    .bodyToMono(type)
                    .block(Duration.ofSeconds(35));
        } catch (AiCallException e) {
            throw e;
        } catch (Exception e) {
            // block() 会把信号异常包进 reactor 层异常，统一翻译成 AiCallException 出槽
            throw translateTransport(e);
        }
    }

    private Flux<ServerSentEvent<String>> stream(String path, Object body, Long userId) {
        // 预序列化为 JSON 字符串再 bodyValue：bodyValue(POJO) + bodyToFlux(SSE) 组合下
        // Jackson 编码器的写出会在流式响应订阅语境中被跳过（实测请求体为空，对端 422
        // "body 缺失"）——String 走 CharSequenceEncoder 的直写路径，绕开该坑；
        // 序列化用注入的单一 ObjectMapper，与同步路径的 DTO 契约同源。
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new AiCallException("请求体序列化失败: " + e.getMessage(), 500, null, false);
        }
        return streamClient.post()
                .uri(path)
                .headers(h -> injectHeaders(h, userId, false))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.isError(),
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(raw -> toAiCallException(resp.statusCode().value(), raw)))
                .bodyToFlux(SSE_STRING);
    }

    /** 统一头注入：门禁 / 链路 / 用户身份 / 管理身份 */
    private void injectHeaders(HttpHeaders headers, Long userId, boolean admin) {
        if (StringUtils.hasText(props.getInternalToken())) {
            headers.set("X-Internal-Token", props.getInternalToken());
        }
        String traceId = UserContext.getTraceId();
        if (StringUtils.hasText(traceId)) {
            headers.set("X-Trace-Id", traceId);
        }
        if (userId != null) {
            headers.set("X-User-Id", String.valueOf(userId));
        }
        if (admin && StringUtils.hasText(props.getAdminToken())) {
            headers.set("X-Admin-Token", props.getAdminToken());
        }
    }

    /** 信封翻译：保留对端 (detail, code, retryable) 三元组，不吞语义 */
    private AiCallException toAiCallException(int status, String rawBody) {
        try {
            AiErrorEnvelope envelope = objectMapper.readValue(rawBody, AiErrorEnvelope.class);
            if (StringUtils.hasText(envelope.getCode())) {
                return new AiCallException(envelope.getDetail(), status, envelope.getCode(),
                        envelope.isRetryableSafe());
            }
        } catch (Exception ignore) {
            // 非 JSON 响应（如网关 502 页面）走下面的兜底
        }
        boolean retryable = status >= 500 || status == 429;
        String detail = StringUtils.hasText(rawBody) ? rawBody : "AI service error " + status;
        return new AiCallException(detail, status, null, retryable);
    }

    /** 网络层故障统一视为可重试临时故障——重试状态机由上层（blog）编排 */
    private AiCallException translateTransport(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        log.warn("AI 服务网络层故障: {}", cause.getMessage());
        return AiCallException.transport("AI service unreachable: " + cause.getMessage());
    }
}
