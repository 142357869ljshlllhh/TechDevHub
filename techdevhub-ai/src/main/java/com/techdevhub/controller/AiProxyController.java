package com.techdevhub.controller;

import com.techdevhub.client.PythonAiClient;
import com.techdevhub.dto.ai.AgentChatRequest;
import com.techdevhub.dto.ai.AgentChatResponse;
import com.techdevhub.dto.ai.AgentHistoryMessage;
import com.techdevhub.dto.ai.AgentHistoryResponse;
import com.techdevhub.dto.ai.ChatReply;
import com.techdevhub.dto.ai.ChatRequest;
import com.techdevhub.dto.ai.ConversationSummary;
import com.techdevhub.dto.ai.RagQueryRequest;
import com.techdevhub.entity.ChatTranscript;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.mapper.ChatConversationMapper;
import com.techdevhub.mapper.ChatTranscriptMapper;
import com.techdevhub.result.Result;
import com.techdevhub.service.SseBridge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private final ChatTranscriptMapper chatTranscriptMapper;
    private final ChatConversationMapper chatConversationMapper;

    /** 历史回放每页条数：一屏足够，更早消息用 before_id 游标翻页 */
    private static final int HISTORY_PAGE_SIZE = 50;

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

    /**
     * 会话历史回放（M7）：读 chat_transcript 持久层——与 LLM 工作记忆（Redis，压缩有损）
     * 是两个存储；这里给用户看的是每轮完整的对话事实。归属内联在查询条件里，
     * 非本人会话返回空列表，不泄露存在性。before_id 游标可选（加载更早消息）。
     */
    @GetMapping("/assistant/history")
    @Operation(summary = "会话历史回放（MySQL 持久层，最新一页按时间正序返回）")
    public Result assistantHistory(@RequestParam("conversation_id") String conversationId,
                                   @RequestParam(value = "before_id", required = false) Long beforeId,
                                   HttpServletRequest httpRequest) {
        Long userId = requireUserId(httpRequest);
        List<ChatTranscript> rows = chatTranscriptMapper.selectByConversation(
                conversationId, userId, beforeId, HISTORY_PAGE_SIZE);
        List<AgentHistoryMessage> messages = new ArrayList<>();
        for (ChatTranscript row : rows) {
            messages.add(new AgentHistoryMessage(row.getRole(), row.getContent(),
                    row.getCreateTime() == null ? null
                            : row.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        }
        // 查询按 id 倒序取最新一页，返回给前端时翻回正序
        Collections.reverse(messages);
        return Result.success(new AgentHistoryResponse(conversationId, messages));
    }

    /** 用户的会话列表（侧边栏）：注册表（标题）+ 最后一条消息预览。 */
    @GetMapping("/assistant/conversations")
    @Operation(summary = "历史会话列表（标题优先，按最后活跃倒序，≤50 个）")
    public Result assistantConversations(HttpServletRequest httpRequest) {
        Long userId = requireUserId(httpRequest);
        List<Map<String, Object>> rows = chatConversationMapper.selectConversationsByUser(userId, 50);
        List<ConversationSummary> summaries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            summaries.add(new ConversationSummary(
                    (String) row.get("conversationId"),
                    (String) row.get("title"),
                    (String) row.get("lastRole"),
                    (String) row.get("lastMessage"),
                    row.get("lastTime") == null ? null
                            : ((java.time.LocalDateTime) row.get("lastTime"))
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        }
        return Result.success(summaries);
    }

    /** 删除会话（仅限归属人）：注册表 + 消息明细一并删除，不可恢复。 */
    @DeleteMapping("/assistant/conversations/{conversationId}")
    @Operation(summary = "删除历史会话（归属校验，注册表与明细同删）")
    public Result deleteConversation(@PathVariable("conversationId") String conversationId,
                                     HttpServletRequest httpRequest) {
        Long userId = requireUserId(httpRequest);
        int deleted = chatConversationMapper.deleteConversation(conversationId, userId);
        if (deleted == 0) {
            // 会话不存在或非本人：一律 404，不泄露存在性
            throw new BusinessException(ErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        chatTranscriptMapper.deleteByConversation(conversationId, userId);
        return Result.success(true);
    }

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
