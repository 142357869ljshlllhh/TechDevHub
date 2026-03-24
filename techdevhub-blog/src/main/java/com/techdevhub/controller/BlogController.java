package com.techdevhub.controller;

import com.techdevhub.annotation.IgnoreToken;
import com.techdevhub.dto.BlogCounterAdjustDTO;
import com.techdevhub.dto.BlogInsertDTO;
import com.techdevhub.dto.BlogPageSelectDTO;
import com.techdevhub.dto.BlogUpdateDTO;
import com.techdevhub.result.Result;
import com.techdevhub.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blogs")
@RequiredArgsConstructor
@Tag(name = "文章模块",description = "未登录用户即可分页查看文章、查看文章详情，登录状态下用户可以发布文章、修改文章、删除文章")
public class BlogController {
    private final BlogService blogService;


    private Long currentUserId(HttpServletRequest request){
        return (Long)request.getAttribute("currentUserId");
    }

    @PostMapping
    @Operation(summary = "发布博客")
    public Result publish(@Valid @RequestBody BlogInsertDTO dto, HttpServletRequest httpServletRequest) {
        return Result.success(blogService.blogInsert(currentUserId(httpServletRequest), dto));
    }

    @PutMapping("/{blogId}")
    @Operation(summary = "修改博客")
    public Result update(@PathVariable Long blogId, @RequestBody BlogUpdateDTO dto, HttpServletRequest httpServletRequest) {
        return Result.success(blogService.blogUpdate(currentUserId(httpServletRequest), blogId, dto));
    }

    @DeleteMapping("/{blogId}")
    @Operation(summary = "删除博客")
    public Result delete(@PathVariable Long blogId, HttpServletRequest httpServletRequest) {
        blogService.blogDelete(currentUserId(httpServletRequest), blogId);
        return Result.success();
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询博客")
    @IgnoreToken
    public Result page(@RequestBody(required = false)BlogPageSelectDTO dto) {
        return Result.success(blogService.page(dto== null ? new BlogPageSelectDTO() : dto));
    }

    @GetMapping("/{blogId}")
    @Operation(summary = "查看博客详情")
    @IgnoreToken
    public Result detail(@PathVariable Long blogId) {
        return Result.success(blogService.detail(blogId));
    }

    @GetMapping("/hot/top10")
    @Operation(summary = "热门文章排行榜")
    @IgnoreToken
    public Result hotTop10() {
        return Result.success(blogService.hotTop10());
    }

    @GetMapping("/mine")
    @Operation(summary = "查看我发布的文章")
    public Result myBlogs(HttpServletRequest request) {
        return Result.success(blogService.currentUserBlogs(currentUserId(request)));
    }

    @PatchMapping("/{blogId}/like-count")
    @IgnoreToken
    @Operation(summary = "调整点赞数")
    public Result adjustLikeCount(@PathVariable Long blogId, @Valid @RequestBody BlogCounterAdjustDTO dto) {
        blogService.adjustLikeCount(blogId, dto);
        return Result.success();
    }

    @PatchMapping("/{blogId}/comment-count")
    @IgnoreToken
    @Operation(summary = "调整评论数")
    public Result adjustCommentCount(@PathVariable Long blogId, @Valid @RequestBody BlogCounterAdjustDTO dto) {
        blogService.adjustCommentCount(blogId, dto);
        return Result.success();
    }

    @GetMapping("/pending")
    @Operation(summary = "查看待审核文章")
    public Result pendingBlogs() {
        return Result.success(blogService.pendingBlogs());
    }
}
