package com.techdevhub.like.controller;

import com.techdevhub.like.service.BlogLikeService;
import com.techdevhub.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Tag(name = "点赞模块")
public class BlogLikeController {

    private final BlogLikeService blogLikeService;

    @PostMapping("/{blogId}")
    @Operation(summary = "点赞文章")
    public Result like(@PathVariable Long blogId, HttpServletRequest request) {
        blogLikeService.like(currentUserId(request), blogId);
        return Result.success();
    }

    @DeleteMapping("/{blogId}")
    @Operation(summary = "取消点赞")
    public Result unlike(@PathVariable Long blogId, HttpServletRequest request) {
        blogLikeService.unlike(currentUserId(request), blogId);
        return Result.success();
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("currentUserId");
    }
}
