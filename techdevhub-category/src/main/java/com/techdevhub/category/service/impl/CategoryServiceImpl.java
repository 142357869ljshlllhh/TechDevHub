package com.techdevhub.category.service.impl;

import com.techdevhub.category.client.UserClient;
import com.techdevhub.category.dto.CategoryCreateDTO;
import com.techdevhub.category.dto.CategoryUpdateDTO;
import com.techdevhub.category.entity.CategoryInfo;
import com.techdevhub.category.mapper.CategoryMapper;
import com.techdevhub.category.service.CategoryService;
import com.techdevhub.category.vo.CategoryVO;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final UserClient userClient;

    @Override
    public List<CategoryVO> list(Long currentUserId) {
        assertAdmin(currentUserId);
        return categoryMapper.selectAll().stream()
                .map(c -> new CategoryVO(c.getId(), c.getCategoryName()))
                .toList();
    }

    @Override
    public void create(Long currentUserId, CategoryCreateDTO dto) {
        assertAdmin(currentUserId);
        if (!StringUtils.hasText(dto.getCategoryName())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (categoryMapper.selectById(dto.getId()) != null) {
            throw new BusinessException(ErrorCode.CATEGORY_ID_ALREADY_EXISTS);
        }
        if (categoryMapper.selectByName(dto.getCategoryName().trim()) != null) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }
        int rows = categoryMapper.insert(dto.getId(), dto.getCategoryName().trim());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CATEGORY_CREATE_FAILED);
        }
    }

    @Override
    public void update(Long currentUserId, Integer id, CategoryUpdateDTO dto) {
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
    public void delete(Long currentUserId, Integer id) {
        assertAdmin(currentUserId);
        int rows = categoryMapper.logicDelete(id);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
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
