package com.techdevhub.category.service;

import com.techdevhub.category.dto.CategoryCreateDTO;
import com.techdevhub.category.dto.CategoryUpdateDTO;
import com.techdevhub.category.vo.CategoryVO;

import java.util.List;

public interface CategoryService {
    List<CategoryVO> list(Long currentUserId);

    void create(Long currentUserId, CategoryCreateDTO dto);

    void update(Long currentUserId, Integer id, CategoryUpdateDTO dto);

    void delete(Long currentUserId, Integer id);
}

