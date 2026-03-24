package com.techdevhub.controller;

import com.techdevhub.dto.AiChatRequest;
import com.techdevhub.service.ConsultantService;
import com.techdevhub.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI 助手")
public class AiAssistantController {

    private final ConsultantService consultantService;

    @PostMapping("/chat")
    @Operation(summary = "AI 对话")
    public Result chat(@Valid @RequestBody AiChatRequest request, HttpServletRequest httpServletRequest) {
        Long currentUserId = (Long) httpServletRequest.getAttribute("currentUserId");
        return Result.success(consultantService.chat(currentUserId, request.getMessage()));
    }

    @DeleteMapping("/memory")
    @Operation(summary = "清空当前用户会话记忆")
    public Result clearMemory(HttpServletRequest httpServletRequest) {
        Long currentUserId = (Long) httpServletRequest.getAttribute("currentUserId");
        consultantService.clearMemory(currentUserId);
        return Result.success();
    }
}
