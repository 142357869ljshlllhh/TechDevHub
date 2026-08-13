package com.techdevhub.category.controller;

import com.techdevhub.annotation.IgnoreToken;
import com.techdevhub.category.dto.CategoryCreateDTO;
import com.techdevhub.category.dto.CategoryRejectDTO;
import com.techdevhub.category.dto.CategoryUpdateDTO;
import com.techdevhub.category.service.CategoryService;
import com.techdevhub.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("currentUserId");
    }

    @IgnoreToken
    @GetMapping
    public Result list(HttpServletRequest request) {
        return Result.success(categoryService.list());
    }

    @PostMapping
    public Result create(@Valid @RequestBody CategoryCreateDTO dto, HttpServletRequest request) {
        categoryService.create(currentUserId(request), dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Long id,
                         @Valid @RequestBody CategoryUpdateDTO dto,
                         HttpServletRequest request) {
        categoryService.update(currentUserId(request), id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id, HttpServletRequest request) {
        categoryService.delete(currentUserId(request), id);
        return Result.success();
    }

    // ===== 管理员审核（审核入口在管理后台） =====
    @GetMapping("/pending")
    public Result listPending(HttpServletRequest request) {
        return Result.success(categoryService.listPending(currentUserId(request)));
    }

    @PostMapping("/{id}/approve")
    public Result approve(@PathVariable Long id, HttpServletRequest request) {
        categoryService.approve(currentUserId(request), id);
        return Result.success();
    }

    @PostMapping("/{id}/reject")
    public Result reject(@PathVariable Long id,
                         @Valid @RequestBody CategoryRejectDTO dto,
                         HttpServletRequest request) {
        categoryService.reject(currentUserId(request), id, dto.getReason());
        return Result.success();
    }
}

