package com.techdevhub.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "用户基本信息VO")
public class UserInformationVO {
    private Long id;
    private String username;
    private String email;
    private Integer followingCount;
    private Integer followerCount;
    private LocalDateTime createTime;
}
