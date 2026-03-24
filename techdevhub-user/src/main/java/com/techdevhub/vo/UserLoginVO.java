package com.techdevhub.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "用户登录成功VO")
public class UserLoginVO {
    private String token;
    private UserInformationVO userInformationVO;
}
