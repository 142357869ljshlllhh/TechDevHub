package com.techdevhub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BlogCounterAdjustDTO{

    @NotNull(message = "增量不能为空")
    private Integer delta;
}
