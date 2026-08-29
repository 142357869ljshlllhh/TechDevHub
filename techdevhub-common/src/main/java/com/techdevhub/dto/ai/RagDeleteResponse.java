package com.techdevhub.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向量库移除结果：deleted_chunks=0 表示本来就不在库中（幂等）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagDeleteResponse {

    @JsonProperty("blog_id")
    private Long blogId;

    @JsonProperty("deleted_chunks")
    private Integer deletedChunks;
}
