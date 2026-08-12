package com.techdevhub.service;

import com.techdevhub.dto.UserLoginDTO;
import com.techdevhub.dto.UserRegisterDTO;
import com.techdevhub.dto.UserUpdateDTO;
import com.techdevhub.dto.UserUpdatePasswordDTO;
import com.techdevhub.vo.UserInformationVO;
import com.techdevhub.vo.UserLoginVO;

public interface UserService {
    void register(UserRegisterDTO userRegisterDTO);

    UserLoginVO login(UserLoginDTO userLoginDTO);

    void checkCurrentUser(Long pathUserId,Long currentUserId);

    UserInformationVO updateUserInformation(Long id,UserUpdateDTO  userUpdateDTO);

    void updatePassword(Long id, UserUpdatePasswordDTO userUpdatePasswordDTO);

    void cancelAccount(Long id);

    void logout(String token);

    void banUser(Long id, Long currentUserId);

    void unbanUser(Long id, Long currentUserId);

    UserInformationVO getPublicProfile(Long id);

    boolean isAdmin(Long id);

    // 按用户名模糊搜索用户（排除已注销/已封禁），用于用户发现与关注
    java.util.List<UserInformationVO> searchUsers(String keyword);
}
