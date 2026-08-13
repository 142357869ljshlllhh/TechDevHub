package com.techdevhub.category.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRejectDTO {
    @NotBlank(message = "驳回原因不能为空")
    private String reason;
}
