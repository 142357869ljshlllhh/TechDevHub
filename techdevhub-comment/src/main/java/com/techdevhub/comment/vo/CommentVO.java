package com.techdevhub.comment.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    private Long id;
    private String content;
    private Long userId;
    private Long blogId;
    private Long parentId;
}
