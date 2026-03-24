package com.techdevhub.comment.service.impl;

import com.techdevhub.comment.client.BlogClient;
import com.techdevhub.comment.dto.BlogCounterAdjustRequest;
import com.techdevhub.comment.dto.CommentCreateDTO;
import com.techdevhub.comment.entity.CommentInfo;
import com.techdevhub.comment.mapper.CommentMapper;
import com.techdevhub.comment.service.CommentService;
import com.techdevhub.comment.vo.CommentVO;
import com.techdevhub.enums.ErrorCode;
import com.techdevhub.exception.BusinessException;
import com.techdevhub.result.Result;
import com.techdevhub.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final BlogClient blogClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public List<CommentVO> listByBlogId(Long blogId) {
        return commentMapper.selectRootComments(blogId).stream().map(this::toVO).toList();
    }

    @Override
    public List<CommentVO> listChildren(Long parentId) {
        return commentMapper.selectChildren(parentId).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO create(Long userId, CommentCreateDTO dto) {
        if (dto.getParentId() != null) {
            CommentInfo parent = commentMapper.selectById(dto.getParentId());
            if (parent == null || parent.getIsDelete() != null && parent.getIsDelete() == 1) {
                throw new BusinessException(ErrorCode.COMMENT_PARENT_INVALID);
            }
            if (!parent.getBlogId().equals(dto.getBlogId())) {
                throw new BusinessException(ErrorCode.COMMENT_PARENT_INVALID);
            }
        }
        CommentInfo commentInfo = new CommentInfo();
        commentInfo.setId(snowflakeIdGenerator.nextId());
        commentInfo.setContent(dto.getContent().trim());
        commentInfo.setUserId(userId);
        commentInfo.setBlogId(dto.getBlogId());
        commentInfo.setParentId(dto.getParentId());
        commentInfo.setIsDelete(0);
        if (commentMapper.insert(commentInfo) == 0) {
            throw new BusinessException(ErrorCode.COMMENT_CREATE_FAILED);
        }
        Result result = blogClient.adjustCommentCount(dto.getBlogId(), new BlogCounterAdjustRequest(1));
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new BusinessException(ErrorCode.COMMENT_CREATE_FAILED);
        }
        return toVO(commentInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long currentUserId, Long commentId) {
        CommentInfo commentInfo = requireComment(commentId);
        if (!commentInfo.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (commentMapper.logicDelete(commentId) == 0) {
            throw new BusinessException(ErrorCode.COMMENT_DELETE_FAILED);
        }
        Result result = blogClient.adjustCommentCount(commentInfo.getBlogId(), new BlogCounterAdjustRequest(-1));
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new BusinessException(ErrorCode.COMMENT_DELETE_FAILED);
        }
    }

    private CommentInfo requireComment(Long commentId) {
        CommentInfo commentInfo = commentMapper.selectById(commentId);
        if (commentInfo == null || commentInfo.getIsDelete() != null && commentInfo.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return commentInfo;
    }

    private CommentVO toVO(CommentInfo commentInfo) {
        return new CommentVO(
                commentInfo.getId(),
                commentInfo.getContent(),
                commentInfo.getUserId(),
                commentInfo.getBlogId(),
                commentInfo.getParentId()
        );
    }
}
