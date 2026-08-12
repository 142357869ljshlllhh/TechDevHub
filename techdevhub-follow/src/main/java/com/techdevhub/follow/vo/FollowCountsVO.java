package com.techdevhub.follow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowCountsVO {
    private Long followingCount;
    private Long followerCount;
}
