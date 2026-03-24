package com.techdevhub.comment.entity;

import lombok.Data;

@Data
public class CommentInfo {

    private Long id;
    private String content;
    private Long userId;
    private Long blogId;
    private Long parentId;
    private Integer isDelete;
}
