package com.techdevhub.follow.controller;

import com.techdevhub.follow.service.FollowService;
import com.techdevhub.follow.vo.FollowersVO;
import com.techdevhub.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/follows")
@RequiredArgsConstructor
@Tag(name = "关注模块")
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{followUserId}")
    @Operation(summary = "关注用户")
    public Result follow(@PathVariable Long followUserId, HttpServletRequest request) {
        followService.follow(currentUserId(request), followUserId);
        return Result.success();
    }

    @DeleteMapping("/{followUserId}")
    @Operation(summary = "取关用户")
    public Result unfollow(@PathVariable Long followUserId, HttpServletRequest request) {
        followService.unfollow(currentUserId(request), followUserId);
        return Result.success();
    }

    @GetMapping("/followers")
    @Operation(summary = "获取粉丝列表（可传 userId 查他人，默认当前用户）")
    public Result getFollowers(@RequestParam(value = "userId", required = false) Long userId, HttpServletRequest request){
        Long target = userId != null ? userId : currentUserId(request);
        List<FollowersVO> followersVO = followService.getFollowers(target);
        return Result.success(followersVO);
    }

    @GetMapping("/following")
    @Operation(summary = "获取关注列表（可传 userId 查他人，默认当前用户）")
    public Result getFollowing(@RequestParam(value = "userId", required = false) Long userId, HttpServletRequest request){
        Long target = userId != null ? userId : currentUserId(request);
        List<FollowersVO> followingVO = followService.getFollowing(target);
        return Result.success(followingVO);
    }

    @GetMapping("/{followUserId}/is-following")
    @Operation(summary = "查询当前用户是否关注了指定用户")
    public Result isFollowing(@PathVariable Long followUserId, HttpServletRequest request){
        return Result.success(followService.isFollowing(currentUserId(request), followUserId));
    }

    @GetMapping("/counts")
    @Operation(summary = "获取关注数/粉丝数（默认当前用户，可传 userId 查他人）")
    public Result counts(@RequestParam(value = "userId", required = false) Long userId, HttpServletRequest request){
        Long target = userId != null ? userId : currentUserId(request);
        return Result.success(followService.getCounts(target));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("currentUserId");
    }
}
