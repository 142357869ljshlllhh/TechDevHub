package com.techdevhub.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoryCreateDTO {
    @NotNull(message = "id不能为空")
    private Integer id;

    @NotBlank(message = "分类名不能为空")
    private String categoryName;
}

