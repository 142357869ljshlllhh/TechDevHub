package com.techdevhub.controller;

import com.techdevhub.client.PythonAiClient;
import com.techdevhub.dto.ai.ModerationCheckRequest;
import com.techdevhub.dto.ai.ModerationResult;
import com.techdevhub.dto.ai.QAResponse;
import com.techdevhub.dto.ai.RagIngestRequest;
import com.techdevhub.dto.ai.RagIngestResult;
import com.techdevhub.dto.ai.RagQueryRequest;
import com.techdevhub.dto.ai.RecheckRequest;
import com.techdevhub.dto.ai.RecheckResponse;
import com.techdevhub.dto.ai.TranscriptBatchRequest;
import com.techdevhub.entity.ChatTranscript;
import com.techdevhub.mapper.ChatTranscriptMapper;
import com.techdevhub.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间内部转发端点（供 blog-service 经 Feign 调用，X-Internal-Token 门禁）。
 *
 * 为什么 blog 不直连 Python：Python 契约（错误信封/两段式错误码/retryable）只在
 * ai-service 感知——AI 能力统一出口，blog 只认 Java 风格接口。
 * 失败语义透传：AiCallException 由全局处理器转 503/429/502 + AI_* 数字码，
 * blog 侧凭数字码还原 retryable，重试状态机的判断依据不丢。
 */
@Slf4j
@RestController
@RequestMapping("/ai/internal")
@RequiredArgsConstructor
@Tag(name = "AI 内部转发（服务间）")
public class AiInternalController {

    private final PythonAiClient pythonAiClient;
    private final ChatTranscriptMapper chatTranscriptMapper;

    @PostMapping("/moderation/check")
    @Operation(summary = "内容审核（发布链路同步调用）")
    public Result moderationCheck(@Valid @RequestBody ModerationCheckRequest request) {
        return Result.success(pythonAiClient.moderationCheck(request));
    }

    @PostMapping("/moderation/recheck")
    @Operation(summary = "批量重审（整批 fail-fast，≤50 篇）")
    public Result moderationRecheck(@Valid @RequestBody RecheckRequest request) {
        return Result.success(pythonAiClient.moderationRecheck(request));
    }

    @PostMapping("/rag/ingest")
    @Operation(summary = "RAG 摄取（幂等，可无限补偿重试）")
    public Result ragIngest(@Valid @RequestBody RagIngestRequest request) {
        return Result.success(pythonAiClient.ragIngest(request));
    }

    @PostMapping("/rag/query")
    @Operation(summary = "RAG 问答（rejected=true 为正常业务结果）")
    public Result ragQuery(@Valid @RequestBody RagQueryRequest request) {
        return Result.success(pythonAiClient.ragQuery(request));
    }

    /**
     * 会话记录批量写入（M7）：Python Agent 轮末回调，把本轮 user/assistant/event
     * 消息落 chat_transcript。数据所有权在 Java 侧——Python 不连业务库，
     * 用户可见的对话历史以此为唯一持久层（Redis 里那份是 LLM 工作记忆，允许有损）。
     */
    @PostMapping("/chat/transcript")
    @Operation(summary = "会话记录批量写入（Python 轮末回调）")
    public Result chatTranscript(@Valid @RequestBody TranscriptBatchRequest request) {
        for (TranscriptBatchRequest.Entry entry : request.getEntries()) {
            ChatTranscript record = new ChatTranscript();
            record.setConversationId(request.getConversationId());
            record.setUserId(request.getUserId());
            record.setRole(entry.getRole());
            record.setContent(entry.getContent());
            chatTranscriptMapper.insert(record);
        }
        return Result.success(request.getEntries().size());
    }
}
