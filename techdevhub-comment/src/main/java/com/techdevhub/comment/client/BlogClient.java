package com.techdevhub.comment.client;

import com.techdevhub.comment.dto.BlogCounterAdjustRequest;
import com.techdevhub.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "blog-service", url = "http://localhost:8082",path = "/blogs")
public interface BlogClient {

    @PatchMapping("/{blogId}/comment-count")
    Result adjustCommentCount(@PathVariable("blogId") Long blogId, @RequestBody BlogCounterAdjustRequest request);
}
