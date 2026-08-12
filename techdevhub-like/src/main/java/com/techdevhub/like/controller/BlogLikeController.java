package com.techdevhub.like.controller;

import com.techdevhub.like.service.BlogLikeService;
import com.techdevhub.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        return Result.success(blogLikeService.like(currentUserId(request), blogId));
    }

    @DeleteMapping("/{blogId}")
    @Operation(summary = "取消点赞")
    public Result unlike(@PathVariable Long blogId, HttpServletRequest request) {
        return Result.success(blogLikeService.unlike(currentUserId(request), blogId));
    }

    @GetMapping("/{blogId}/status")
    @Operation(summary = "查询当前用户是否对指定博客点赞")
    public Result status(@PathVariable Long blogId, HttpServletRequest request) {
        return Result.success(blogLikeService.isLiked(currentUserId(request), blogId));
    }

    @GetMapping("/status")
    @Operation(summary = "批量查询当前用户对多篇博客的点赞态，blogIds 以逗号分隔")
    public Result batchStatus(@RequestParam("blogIds") String blogIds, HttpServletRequest request) {
        java.util.List<Long> ids = parseBlogIds(blogIds);
        return Result.success(blogLikeService.batchIsLiked(currentUserId(request), ids));
    }

    private java.util.List<Long> parseBlogIds(String blogIds) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        if (blogIds == null || blogIds.isBlank()) {
            return ids;
        }
        for (String s : blogIds.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                try {
                    ids.add(Long.parseLong(t));
                } catch (NumberFormatException ignored) {
                    // 跳过非法 id
                }
            }
        }
        return ids;
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("currentUserId");
    }
}
