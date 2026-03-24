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
}
