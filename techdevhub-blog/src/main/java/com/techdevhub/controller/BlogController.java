package com.techdevhub.controller;

import com.techdevhub.annotation.IgnoreToken;
import com.techdevhub.config.JwtProperties;
import com.techdevhub.dto.BlogCounterAdjustDTO;
import com.techdevhub.dto.BlogInsertDTO;
import com.techdevhub.dto.BlogPageSelectDTO;
import com.techdevhub.dto.BlogUpdateDTO;
import com.techdevhub.dto.ModerationRecheckDTO;
import com.techdevhub.jwt.JWTUtil;
import com.techdevhub.result.Result;
import com.techdevhub.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/blogs")
@RequiredArgsConstructor
@Tag(name = "文章模块",description = "未登录用户即可分页查看文章、查看文章详情，登录状态下用户可以发布文章、修改文章、删除文章")
public class BlogController {
    private final BlogService blogService;
    private final JWTUtil jwtUtil;
    private final JwtProperties jwtProperties;


    private Long currentUserId(HttpServletRequest request){
        return (Long)request.getAttribute("currentUserId");
    }

    /**
     * 宽容解析当前用户：detail 是 @IgnoreToken 端点，拦截器不会解析 token，
     * 而"作者查看自己未发布的草稿/审核中文章"需要身份。无 token、token 无效一律
     * 返回 null——绝不影响匿名读已发布文章的主路径。
     */
    private Long currentUserIdOrNull(HttpServletRequest request) {
        Long fromContext = currentUserId(request);
        if (fromContext != null) {
            return fromContext;
        }
        String token = request.getHeader(jwtProperties.getHeaderName());
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            return jwtUtil.getUserId(token);
        } catch (Exception e) {
            return null;
        }
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
    public Result detail(@PathVariable Long blogId, HttpServletRequest request) {
        return Result.success(blogService.detail(blogId, currentUserIdOrNull(request)));
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
    @Operation(summary = "查看待审核文章（管理员）")
    public Result pendingBlogs() {
        blogService.assertAdmin();
        return Result.success(blogService.pendingBlogs());
    }

    @PatchMapping("/{blogId}/status")
    @Operation(summary = "管理员修改文章审核状态（1=通过,2=驳回/下架）")
    public Result changeStatus(@PathVariable Long blogId,
                               @RequestParam Integer status) {
        blogService.assertAdmin();
        blogService.changeStatus(blogId, status);
        return Result.success();
    }

    @PostMapping("/moderation/recheck")
    @Operation(summary = "管理员一键 AI 重审（≤50 篇/批；审核服务故障时整批 503，可整批重试）")
    public Result recheck(@Valid @RequestBody ModerationRecheckDTO dto) {
        blogService.assertAdmin();
        return Result.success(blogService.recheckBlogs(dto.getBlogIds()));
    }

    // ---------- 向量索引管理（M10，管理员） ----------

    @GetMapping("/rag/index-status")
    @Operation(summary = "向量索引状态列表（管理员；status 可选 pending/ok/failed/removed）")
    public Result ragIndexStatus(@RequestParam(required = false) String status) {
        blogService.assertAdmin();
        return Result.success(blogService.ragIndexStatus(status));
    }

    @PostMapping("/rag/reingest/{blogId}")
    @Operation(summary = "单篇重新入库（失败补偿重试）")
    public Result ragReingest(@PathVariable Long blogId) {
        blogService.assertAdmin();
        blogService.reingestRag(blogId);
        return Result.success();
    }

    @PostMapping("/rag/rebuild")
    @Operation(summary = "全量重建向量索引（所有已发布文章重新入库，幂等）")
    public Result ragRebuild() {
        blogService.assertAdmin();
        return Result.success(blogService.rebuildRag());
    }
}
