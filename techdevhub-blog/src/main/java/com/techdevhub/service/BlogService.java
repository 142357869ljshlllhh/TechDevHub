package com.techdevhub.service;

import com.techdevhub.dto.BlogCounterAdjustDTO;
import com.techdevhub.dto.BlogInsertDTO;
import com.techdevhub.dto.BlogUpdateDTO;
import com.techdevhub.result.PageResult;
import com.techdevhub.vo.BlogDetailVO;
import com.techdevhub.dto.BlogPageSelectDTO;
import com.techdevhub.vo.BlogSummaryVO;

import java.util.List;

public interface BlogService {
    BlogDetailVO blogInsert(Long userId, BlogInsertDTO dto);

    BlogDetailVO blogUpdate(Long userId, Long blogId, BlogUpdateDTO dto);

    void blogDelete(Long currentUserId, Long blogId);

    BlogDetailVO detail(Long blogId);

    PageResult<BlogSummaryVO> page(BlogPageSelectDTO dto);

    List<BlogSummaryVO> hotTop10();

    List<BlogSummaryVO> currentUserBlogs(Long currentUserId);

    void adjustLikeCount(Long blogId, BlogCounterAdjustDTO dto);

    void adjustCommentCount(Long blogId, BlogCounterAdjustDTO dto);

    List<BlogSummaryVO> pendingBlogs();

    void changeStatus(Long blogId, Integer status);

}
