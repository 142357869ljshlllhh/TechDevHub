package com.techdevhub.like.entity;

import lombok.Data;

@Data
public class BlogLikeInfo {

    private Long id;
    private Long userId;
    private Long blogId;
    private Integer isDelete;
}
