package com.techdevhub.controller;

import com.techdevhub.annotation.IgnoreToken;
import com.techdevhub.dto.UserLoginDTO;
import com.techdevhub.dto.UserRegisterDTO;
import com.techdevhub.dto.UserUpdateDTO;
import com.techdevhub.dto.UserUpdatePasswordDTO;
import com.techdevhub.result.Result;
import com.techdevhub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "用户模块",description = "用户注册、登录、修改账户信息、修改密码、注销账户、退出登录")
public class UserController {
    private final UserService userService;

    private Long currentUserId(HttpServletRequest request){
        return (Long)request.getAttribute("currentUserId");
    }

    @IgnoreToken
    @PostMapping("/register")
    @Operation(summary = "用户注册接口",description = "昵称、邮箱、密码必填，昵称和邮箱唯一不可重复")
    @ApiResponses({
            @ApiResponse(responseCode = "200",description = "用户注册成功"),
            @ApiResponse(responseCode = "1000",description = "昵称已被使用",content = @Content),
            @ApiResponse(responseCode = "1001",description = "邮箱已被使用",content = @Content),
            @ApiResponse(responseCode = "1002",description = "注册失败",content = @Content)
    })
    public Result register(@Valid @RequestBody UserRegisterDTO userRegisterDTO){
        userService.register(userRegisterDTO);
        return Result.success();
    }

    @IgnoreToken
    @PostMapping("/login")
    @Operation(summary = "用户登录接口",description = "邮箱、密码必填，邮箱、密码须正确")
    @ApiResponses({
            @ApiResponse(responseCode = "200",description = "登录成功"),
            @ApiResponse(responseCode = "1003",description = "该邮箱没有创建账户",content = @Content),
            @ApiResponse(responseCode = "1004",description = "密码错误",content = @Content),
            @ApiResponse(responseCode = "409",description = "账号已被注销",content = @Content),
    })
    public Result login(@Valid @RequestBody UserLoginDTO userLoginDTO){
        return Result.success(userService.login(userLoginDTO));
    }

    @IgnoreToken
    @GetMapping("/{id}/profile")
    @Operation(summary = "查询用户基本信息接口")
    public Result profile(@PathVariable Long id) {
        return Result.success(userService.getPublicProfile(id));
    }

    @IgnoreToken
    @GetMapping("/{id}/admin-status")
    @Operation(summary = "查询是否管理员接口")
    public Result adminStatus(@PathVariable Long id) {
        return Result.success(userService.isAdmin(id));
    }

    @PutMapping("{id}")
    @Operation(summary = "用户修改信息信息接口",description = "只能修改自己的账户信息，修改后昵称已被使用、修改后邮箱已被使用",security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200",description = "登录成功"),
            @ApiResponse(responseCode = "1005",description = "不可以修改别人的信息")
    })
    public Result updateInformation(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO userUpdateDTO, HttpServletRequest request){
        Long currentId = currentUserId(request);
        userService.checkCurrentUser(id,currentId);
        return Result.success(userService.updateUserInformation(id,userUpdateDTO));
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "修改密码", description = "需要先校验原密码，新密码不能和原密码相同", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200",description = "修改密码成功"),
            @ApiResponse(responseCode = "1008",description = "账户信息不存在"),
            @ApiResponse(responseCode = "1004",description = "密码错误"),
            @ApiResponse(responseCode = "1009",description = "新密码和旧密码相同"),
            @ApiResponse(responseCode = "1010",description = "修改密码失败")
    })
    public Result updatePassword(@PathVariable Long id,
                                 @Valid @RequestBody UserUpdatePasswordDTO dto,
                                 HttpServletRequest httpServletRequest) {
        Long currentUserId = currentUserId(httpServletRequest);
        userService.checkCurrentUser(id, currentUserId);
        userService.updatePassword(currentUserId, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "注销账号", description = "逻辑删除用户，并释放用户名与邮箱供后续复用", security = @SecurityRequirement(name = "BearerAuth"))
    public Result cancelAccount(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        userService.checkCurrentUser(id, currentUserId);
        userService.cancelAccount(currentUserId);
        userService.logout((String) request.getAttribute("currentToken"));
        return Result.success();
    }

    @PatchMapping("/{id}/ban")
    @Operation(summary = "封禁用户", security = @SecurityRequirement(name = "BearerAuth"))
    public Result banUser(@PathVariable Long id, HttpServletRequest request) {
        userService.banUser(id, currentUserId(request));
        return Result.success();
    }

    @PatchMapping("/{id}/unban")
    @Operation(summary = "解封用户", security = @SecurityRequirement(name = "BearerAuth"))
    public Result unbanUser(@PathVariable Long id, HttpServletRequest request) {
        userService.unbanUser(id, currentUserId(request));
        return Result.success();
    }
}
