package com.techdevhub.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "用户类")
public class UserInfo {
    private Long id;
    private String username;
    private String email;
    private String password;
    private Integer isDelete = 0;
    private Integer status = 0;
    private Integer followingCount = 0;
    private Integer followerCount = 0;
    private LocalDateTime createTime =  LocalDateTime.now();
}
