package com.techdevhub.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RAG 引用来源 —— answer 正文中的 [blog_id] 引用与此列表对应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagSource {

    private Long blogId;

    private String title;

    private String snippet;

    private Double score;
}
