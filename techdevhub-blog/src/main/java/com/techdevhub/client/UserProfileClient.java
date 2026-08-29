package com.techdevhub.client;

import com.techdevhub.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "user-service", url = "${techdevhub.feign.user-service-url:http://localhost:8081}",path = "/users")
public interface UserProfileClient {

    @GetMapping("/{id}/profile")
    Result getProfile(@PathVariable("id") Long id);

    @GetMapping("/{id}/admin-status")
    Result isAdmin(@PathVariable("id") Long id);

    @PostMapping("/profiles/batch")
    Result batchGetProfiles(@RequestBody List<Long> ids);
}
