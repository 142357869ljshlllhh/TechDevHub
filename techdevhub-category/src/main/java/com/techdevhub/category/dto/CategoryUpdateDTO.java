package com.techdevhub.category.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryUpdateDTO {
    @NotBlank(message = "分类名不能为空")
    private String categoryName;
}

