package com.techdevhub.service.impl;

import com.techdevhub.dto.UserLoginDTO;
import com.techdevhub.dto.UserRegisterDTO;
import com.techdevhub.dto.UserUpdateDTO;
import com.techdevhub.dto.UserUpdatePasswordDTO;
import com.techdevhub.entity.UserInfo;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.jwt.JWTUtil;
import com.techdevhub.mapper.UserMapper;
import com.techdevhub.service.UserService;
import com.techdevhub.util.SnowflakeIdGenerator;
import com.techdevhub.vo.UserInformationVO;
import com.techdevhub.vo.UserLoginVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String LOGOUT_TOKEN_PREFIX = "user:logout:token:";
    private static final String USER_PROFILE_CACHE_PREFIX = "user:profile:";
    private static final String DELETE_SUFFIX = "#deleted#";

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final JWTUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterDTO userRegisterDTO) {
        checkEmailUnique(userRegisterDTO.getEmail());
        checkUsernameUnique(userRegisterDTO.getUsername());
        String email = userRegisterDTO.getEmail().trim();
        String username = userRegisterDTO.getUsername().trim();
        String password = bCryptPasswordEncoder.encode(userRegisterDTO.getPassword());
        Long id = snowflakeIdGenerator.nextId();
        if(userMapper.register(id,username,password,email) == 0) {
            throw new BusinessException(ErrorCode.USER_REGISTER_FAILED);
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setId(id);
        userInfo.setUsername(username);
        userInfo.setEmail(email);
        cacheUserProfile(userInfo);
    }

    @Override
    public UserLoginVO login(UserLoginDTO dto){
        UserInfo userInfo = userMapper.selectUserByEmail(dto.getEmail());
        if(userInfo == null){
            throw new BusinessException(ErrorCode.USER_EMAIL_NOT_EXIST);
        }
        if(userInfo.getIsDelete() == 1){
            throw new BusinessException(ErrorCode.ACCOUNT_IS_DELETE);
        }
        if(userInfo.getIsDelete() == 2){
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if(!bCryptPasswordEncoder.matches(dto.getPassword(),userInfo.getPassword())){
            throw new BusinessException(ErrorCode.USER_PASSWORD_IS_WORONG);
        }
        String token = jwtUtil.gengerateToken(userInfo.getId());
        return new UserLoginVO(token,toUserInformationVO(userInfo));
    }

    @Override
    public void checkCurrentUser(Long pathUserId,Long currentUserId) {
        if(currentUserId == null || !currentUserId.equals(pathUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        UserInfo currentUser = userMapper.selectUserById(currentUserId);
        if (currentUser == null || currentUser.getIsDelete() == 1 || currentUser.getIsDelete() == 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInformationVO updateUserInformation(Long id,UserUpdateDTO updateDTO){
        UserInfo userInfo = userMapper.selectUserById(id);
        String username;
        String email;
        if((updateDTO.getEmail() == null || updateDTO.getEmail().trim().isEmpty()) && (updateDTO.getUsername() == null || updateDTO.getUsername().trim().isEmpty())) {
            throw new BusinessException(ErrorCode.USER_UPDATE_ALL_ARE_NULL);
        }
        if(updateDTO.getUsername() != null){
            username = updateDTO.getUsername();
        }else{
            checkUsernameUnique(updateDTO.getUsername());
            username = updateDTO.getUsername();
            userInfo.setUsername(username);
        }
        if(updateDTO.getEmail() != null){
            email = updateDTO.getEmail();
        }else {
            checkEmailUnique(updateDTO.getEmail());
            email = updateDTO.getEmail();
            userInfo.setEmail(email);
        }
        if(userMapper.updateInformation(id,username,email) == 0){
            throw new BusinessException(ErrorCode.USER_UPDATEINFORMATION_FAILED);
        }
        cacheUserProfile(userInfo);
        return toUserInformationVO(userInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long id, UserUpdatePasswordDTO dto) {
        UserInfo userInfo = userMapper.selectUserById(id);
        if(userInfo == null){
            throw new BusinessException(ErrorCode.USER_UPDATE_ALL_ARE_NULL);
        }
        if (!bCryptPasswordEncoder.matches(dto.getOldPassword(), userInfo.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_IS_WORONG);
        }
        if (bCryptPasswordEncoder.matches(dto.getNewPassword(), userInfo.getPassword())) {
            throw new BusinessException(ErrorCode.USER_NEW_PASSWORD_SAME_AS_OLD);
        }
        if(userMapper.updatePassword(id, bCryptPasswordEncoder.encode(dto.getNewPassword()))==0){
            throw new BusinessException(ErrorCode.USER_UPDATE_PASSWORD_FAILED);
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAccount(Long id) {
        UserInfo userInfo = userMapper.selectUserById(id);
        if(userInfo == null){
            throw new BusinessException(ErrorCode.USER_NOT_EXISTS);
        }
        String deletedUsername = appendDeleteMark(userInfo.getUsername(), id);
        String deletedEmail = appendDeleteMark(userInfo.getEmail(), id);
        userMapper.logicDelete(id, deletedUsername, deletedEmail);
        stringRedisTemplate.delete(USER_PROFILE_CACHE_PREFIX + id);
    }

    public void logout(String token){
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Claims claims = jwtUtil.parseToken(token);
        LocalDateTime expirationTime = LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneId.systemDefault());
        long seconds = Duration.between(LocalDateTime.now(), expirationTime).getSeconds();
        if (seconds <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(LOGOUT_TOKEN_PREFIX + token, "1", Duration.ofSeconds(seconds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void banUser(Long id, Long currentUserId) {
        assertAdmin(currentUserId);
        if (id.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (userMapper.banUser(id) == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_EXISTS);
        }
        stringRedisTemplate.delete(USER_PROFILE_CACHE_PREFIX + id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbanUser(Long id, Long currentUserId) {
        assertAdmin(currentUserId);
        if (userMapper.unbanUser(id) == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_EXISTS);
        }
        UserInfo userInfo = userMapper.selectUserById(id);
        if (userInfo != null) {
            cacheUserProfile(userInfo);
        }
    }

    @Override
    public UserInformationVO getPublicProfile(Long id) {
        UserInfo userInfo = userMapper.selectUserById(id);
        if (userInfo == null || userInfo.getIsDelete() == null || userInfo.getIsDelete() != 0) {
            throw new BusinessException(ErrorCode.USER_NOT_EXISTS);
        }
        return toUserInformationVO(userInfo);
    }

    @Override
    public boolean isAdmin(Long id) {
        UserInfo userInfo = userMapper.selectUserById(id);
        return userInfo != null
                && userInfo.getIsDelete() != null
                && userInfo.getIsDelete() == 0
                && userInfo.getStatus() != null
                && userInfo.getStatus() == 1;
    }






    private void checkUsernameUnique(String username) {
        if(userMapper.selectUserByUsername(username) !=null){
            throw new BusinessException(ErrorCode.USER_USERNAME_IS_USED);
        }
    }

    private void checkEmailUnique(String email) {
        if(userMapper.selectUserByEmail(email) !=null){
            throw new BusinessException(ErrorCode.USER_EMAIL_IS_USED);
        }
    }

    private UserInformationVO toUserInformationVO(UserInfo userInfo) {
        UserInformationVO userInformationVO = new UserInformationVO();
        userInformationVO.setId(userInfo.getId());
        userInformationVO.setUsername(userInfo.getUsername());
        userInformationVO.setEmail(userInfo.getEmail());
        userInformationVO.setCreateTime(userInfo.getCreateTime());
        userInformationVO.setFollowingCount(userInfo.getFollowingCount());
        userInformationVO.setFollowerCount(userInfo.getFollowerCount());
        return userInformationVO;
    }

    private String appendDeleteMark(String value, Long id) {
        return value + DELETE_SUFFIX + id;
    }

    private void assertAdmin(Long currentUserId) {
        UserInfo operator = userMapper.selectUserById(currentUserId);
        if (operator == null || operator.getIsDelete() != 0 || operator.getStatus() == null || operator.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void cacheUserProfile(UserInfo userInfo) {
        if (userInfo == null || userInfo.getId() == null || !StringUtils.hasText(userInfo.getUsername())) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(toUserInformationVO(userInfo));
            stringRedisTemplate.opsForValue().set(USER_PROFILE_CACHE_PREFIX + userInfo.getId(), json);
        } catch (JsonProcessingException ignored) {
            // ignore cache write failures, database remains source of truth
        }
    }
}
