package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RAG 摄取响应。同 blog_id 重摄=覆盖（幂等），Java 侧可放心无限补偿重试。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagIngestResult {

    @JsonProperty("blog_id")
    private Long blogId;

    @JsonProperty("chunk_count")
    private Integer chunkCount;
}
