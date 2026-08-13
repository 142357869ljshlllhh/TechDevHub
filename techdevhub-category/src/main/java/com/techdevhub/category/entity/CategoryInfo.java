package com.techdevhub.category.entity;

import lombok.Data;

@Data
public class CategoryInfo {
    private Long id;
    private String categoryName;
    private Integer isDelete;
    // 审核状态：0 待审 / 1 通过 / 2 驳回（普通用户提交后为待审，管理员通过后全员可见）
    private Integer status;
    // 申请人
    private Long creatorId;
    // 驳回原因（驳回时必填）
    private String rejectReason;
}
