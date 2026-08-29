package com.techdevhub.controller;

import com.techdevhub.client.PythonAiClient;
import com.techdevhub.dto.ai.AgentChatRequest;
import com.techdevhub.dto.ai.AgentChatResponse;
import com.techdevhub.dto.ai.ChatReply;
import com.techdevhub.dto.ai.ChatRequest;
import com.techdevhub.dto.ai.QAResponse;
import com.techdevhub.dto.ai.RagQueryRequest;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.result.Result;
import com.techdevhub.service.SseBridge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Python AI 服务前端代理端点（v2 适配层）。
 *
 * 命名空间与旧 langchain4j 端点（/ai/chat、/ai/memory）完全隔离——共存决策：
 * 旧链路零改动，新链路全部挂 /ai/v2、/ai/qa、/ai/assistant 前缀，前端按需迁移。
 *
 * X-User-Id 语义：Python 侧人类端点凭此做会话归属校验（403）与写工具身份强制覆盖，
 * 所以必须取 JWT 解析出的 currentUserId，绝不信任请求体里自称的 userId。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI 服务代理（Python 微服务适配层）")
public class AiProxyController {

    private final PythonAiClient pythonAiClient;

    private static Long requireUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    // ---------- RAG 问答 ----------

    @PostMapping("/qa")
    @Operation(summary = "知识库问答（rejected=true 表示知识库未覆盖，属正常结果）")
    public Result qa(@Valid @RequestBody RagQueryRequest request) {
        return Result.success(pythonAiClient.ragQuery(request));
    }

    // ---------- 多轮对话 ----------

    @PostMapping("/v2/chat")
    @Operation(summary = "多轮对话（Python 侧会话记忆，Redis db2）")
    public Result chat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        Long userId = requireUserId(httpRequest);
        ChatReply reply = pythonAiClient.chat(request, userId);
        return Result.success(reply);
    }

    @PostMapping(value = "/v2/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "多轮对话流式（SSE 帧协议：ping/delta/error/[DONE]）")
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        Long userId = requireUserId(httpRequest);
        // 请求体里带 traceId 头由 TraceInterceptor 已放入 UserContext，桥接时自动带上
        return SseBridge.bridge(pythonAiClient.chatStream(request, userId));
    }

    // ---------- 社区助手（两跳写确认） ----------

    @PostMapping("/assistant/chat")
    @Operation(summary = "社区助手对话（pending_action 非空时需用户确认后二次调用）")
    public Result assistantChat(@Valid @RequestBody AgentChatRequest request, HttpServletRequest httpRequest) {
        Long userId = requireUserId(httpRequest);
        AgentChatResponse response = pythonAiClient.agentChat(request, userId);
        return Result.success(response);
    }

    @PostMapping(value = "/assistant/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "社区助手流式（额外含 pending_action 帧）")
    public SseEmitter assistantChatStream(@Valid @RequestBody AgentChatRequest request,
                                          HttpServletRequest httpRequest) {
        Long userId = requireUserId(httpRequest);
        return SseBridge.bridge(pythonAiClient.agentChatStream(request, userId));
    }
}
