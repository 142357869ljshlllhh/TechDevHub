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

    /**
     * 详情：已发布文章任何人可见；status=0（审核中/草稿）仅作者本人可见（currentUserId
     * 传入 null 表示匿名），他人与非作者一律 NOT_FOUND，不泄露存在性。
     */
    BlogDetailVO detail(Long blogId, Long currentUserId);

    PageResult<BlogSummaryVO> page(BlogPageSelectDTO dto);

    List<BlogSummaryVO> hotTop10();

    List<BlogSummaryVO> currentUserBlogs(Long currentUserId);

    void adjustLikeCount(Long blogId, BlogCounterAdjustDTO dto);

    void adjustCommentCount(Long blogId, BlogCounterAdjustDTO dto);

    List<BlogSummaryVO> pendingBlogs();

    void changeStatus(Long blogId, Integer status);

    void assertAdmin();

    /**
     * 创建博客草稿（供 Python Agent 写工具 create_draft 回调，T7）。
     * 草稿 status=0 不触发审核、不进热榜；返回雪花 id。
     */
    Long createDraft(Long userId, String title, String content);

    /** 查询用户草稿列表（status=0 未发布文章，供 Python 工具 get_drafts 回调）。 */
    java.util.List<com.techdevhub.entity.BlogInfo> draftsOf(Long userId);

    /**
     * 管理端批量重审（T8）：按 blogId 加载文章并同步调用重审，结果按 verdict 流转状态。
     * 对端整批 fail-fast：任一篇组件故障整批 503（幂等可整批重试）。
     */
    int recheckBlogs(java.util.List<Long> blogIds);

}
