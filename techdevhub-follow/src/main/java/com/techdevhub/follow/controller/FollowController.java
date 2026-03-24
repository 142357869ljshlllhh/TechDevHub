package com.techdevhub.follow.controller;

import com.techdevhub.follow.service.FollowService;
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

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("currentUserId");
    }
}
