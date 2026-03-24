package com.techdevhub.category.client;

import com.techdevhub.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "http://localhost:8081",path = "/users")
public interface UserClient {

    @GetMapping("/{id}/admin-status")
    Result isAdmin(@PathVariable("id") Long id);
}

