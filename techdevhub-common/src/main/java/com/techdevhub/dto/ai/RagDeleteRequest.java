package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向量库移除请求 —— 按博客 ID 删除该文章的全部 chunk（生命周期：删除/下架/编辑出库）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagDeleteRequest {

    @JsonProperty("blog_id")
    private Long blogId;
}
