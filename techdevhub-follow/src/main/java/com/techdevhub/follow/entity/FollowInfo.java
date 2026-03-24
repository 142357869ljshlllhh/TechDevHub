package com.techdevhub.follow.entity;

import lombok.Data;

@Data
public class FollowInfo {

    private Long id;
    private Long userId;
    private Long followUserId;
    private Integer isDelete;
}
