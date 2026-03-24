package com.techdevhub.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentCreateDTO {

    @NotBlank(message = "评论内容不能为空")
    private String content;

    @NotNull(message = "文章ID不能为空")
    private Long blogId;

    private Long parentId;
}
