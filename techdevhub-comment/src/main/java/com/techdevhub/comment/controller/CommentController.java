package com.techdevhub.comment.controller;

import com.techdevhub.annotation.IgnoreToken;
import com.techdevhub.comment.dto.CommentCreateDTO;
import com.techdevhub.comment.service.CommentService;
import com.techdevhub.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Tag(name = "评论模块")
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/blog/{blogId}")
    @IgnoreToken
    @Operation(summary = "查看文章评论")
    public Result listByBlogId(@PathVariable Long blogId) {
        return Result.success(commentService.listByBlogId(blogId));
    }

    @GetMapping("/{parentId}/children")
    @IgnoreToken
    @Operation(summary = "查看评论子评论")
    public Result listChildren(@PathVariable Long parentId) {
        return Result.success(commentService.listChildren(parentId));
    }

    @PostMapping
    @Operation(summary = "发布评论或回复评论")
    public Result create(@Valid @RequestBody CommentCreateDTO dto, HttpServletRequest request) {
        return Result.success(commentService.create(currentUserId(request), dto));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "删除评论")
    public Result delete(@PathVariable Long commentId, HttpServletRequest request) {
        commentService.delete(currentUserId(request), commentId);
        return Result.success();
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("currentUserId");
    }
}
