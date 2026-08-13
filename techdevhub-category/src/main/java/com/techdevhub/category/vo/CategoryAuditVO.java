package com.techdevhub.category.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CategoryAuditVO {
    private Long id;
    private String categoryName;
    private Integer status;
    private Long creatorId;
    private String rejectReason;
}
