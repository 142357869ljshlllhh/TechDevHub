package com.techdevhub.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RAG 问答请求 —— Python POST /api/v1/rag/query，全小写字段无需映射。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryRequest {

    @NotBlank(message = "query 不能为空")
    @Size(max = 2000, message = "query 超长（对端上限 2000 字）")
    private String query;
}
