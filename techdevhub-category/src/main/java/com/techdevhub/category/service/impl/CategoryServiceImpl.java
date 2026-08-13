package com.techdevhub.category.service.impl;

import com.techdevhub.category.client.UserClient;
import com.techdevhub.category.dto.CategoryCreateDTO;
import com.techdevhub.category.dto.CategoryUpdateDTO;
import com.techdevhub.category.entity.CategoryInfo;
import com.techdevhub.category.mapper.CategoryMapper;
import com.techdevhub.category.service.CategoryService;
import com.techdevhub.category.vo.CategoryAuditVO;
import com.techdevhub.category.vo.CategoryVO;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.result.Result;
import com.techdevhub.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final UserClient userClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public List<CategoryVO> list() {
        // 公开列表：仅返回已通过(status=1)的类目；待审项由管理员在后台审核，不对外暴露
        return categoryMapper.selectAll().stream()
                .map(c -> new CategoryVO(c.getId(), c.getCategoryName()))
                .toList();
    }

    @Override
    public void create(Long currentUserId, CategoryCreateDTO dto) {
        // 普通登录用户即可提交新类目，进入待审核（status=0）；管理员审核通过后全员可用
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!StringUtils.hasText(dto.getCategoryName())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (categoryMapper.selectByName(dto.getCategoryName().trim()) != null) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }
        // id 由后端雪花算法生成，避免前端传入导致主键冲突
        Long id = snowflakeIdGenerator.nextId();
        int rows = categoryMapper.insert(id, dto.getCategoryName().trim(), 0, currentUserId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CATEGORY_CREATE_FAILED);
        }
    }

    @Override
    public void update(Long currentUserId, Long id, CategoryUpdateDTO dto) {
        assertAdmin(currentUserId);
        CategoryInfo old = categoryMapper.selectById(id);
        if (old == null || (old.getIsDelete() != null && old.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        String newName = dto.getCategoryName() == null ? "" : dto.getCategoryName().trim();
        if (!StringUtils.hasText(newName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (newName.equals(old.getCategoryName())) {
            return;
        }
        CategoryInfo sameName = categoryMapper.selectByName(newName);
        if (sameName != null && !sameName.getId().equals(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }
        int rows = categoryMapper.updateName(id, newName);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CATEGORY_UPDATE_FAILED);
        }
    }

    @Override
    public void delete(Long currentUserId, Long id) {
        assertAdmin(currentUserId);
        int rows = categoryMapper.logicDelete(id);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    @Override
    public List<CategoryAuditVO> listPending(Long currentUserId) {
        assertAdmin(currentUserId);
        return categoryMapper.selectPending().stream()
                .map(c -> new CategoryAuditVO(c.getId(), c.getCategoryName(), c.getStatus(), c.getCreatorId(), c.getRejectReason()))
                .toList();
    }

    @Override
    public void approve(Long currentUserId, Long id) {
        assertAdmin(currentUserId);
        CategoryInfo old = categoryMapper.selectById(id);
        if (old == null || (old.getIsDelete() != null && old.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        categoryMapper.approve(id);
    }

    @Override
    public void reject(Long currentUserId, Long id, String reason) {
        assertAdmin(currentUserId);
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        CategoryInfo old = categoryMapper.selectById(id);
        if (old == null || (old.getIsDelete() != null && old.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        categoryMapper.reject(id, reason.trim());
    }

    private void assertAdmin(Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Result result = userClient.isAdmin(currentUserId);
        Object data = result == null ? null : result.getData();
        boolean isAdmin = data instanceof Boolean ? (Boolean) data : Boolean.parseBoolean(String.valueOf(data));
        if (!isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
