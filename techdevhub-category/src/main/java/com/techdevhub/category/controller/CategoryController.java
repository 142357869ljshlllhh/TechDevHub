package com.techdevhub.category.controller;

import com.techdevhub.category.dto.CategoryCreateDTO;
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

    @GetMapping
    public Result list(HttpServletRequest request) {
        return Result.success(categoryService.list(currentUserId(request)));
    }

    @PostMapping
    public Result create(@Valid @RequestBody CategoryCreateDTO dto, HttpServletRequest request) {
        categoryService.create(currentUserId(request), dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id,
                         @Valid @RequestBody CategoryUpdateDTO dto,
                         HttpServletRequest request) {
        categoryService.update(currentUserId(request), id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id, HttpServletRequest request) {
        categoryService.delete(currentUserId(request), id);
        return Result.success();
    }
}

