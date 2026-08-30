package com.techdevhub.follow.client;

import com.techdevhub.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service",url="${techdevhub.feign.user-service-url:http://localhost:8081}",path = "/users")
public interface UserClient {
    @GetMapping("/{id}/profile")
    Result getProfile(@PathVariable("id") Long id);

    /** 批量公开资料：user 侧已过滤注销/封禁用户，返回列表即"有效用户" */
    @PostMapping("/profiles/batch")
    Result batchGetProfiles(@RequestBody java.util.List<Long> ids);
}
