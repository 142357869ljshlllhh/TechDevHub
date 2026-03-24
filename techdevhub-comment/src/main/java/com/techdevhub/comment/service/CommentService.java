package com.techdevhub.comment.service;

import com.techdevhub.comment.dto.CommentCreateDTO;
import com.techdevhub.comment.vo.CommentVO;

import java.util.List;

public interface CommentService {

    List<CommentVO> listByBlogId(Long blogId);

    List<CommentVO> listChildren(Long parentId);

    CommentVO create(Long userId, CommentCreateDTO dto);

    void delete(Long currentUserId, Long commentId);
}
