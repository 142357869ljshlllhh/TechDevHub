package com.techdevhub.follow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.techdevhub")
public class TechdevhubFollowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechdevhubFollowApplication.class, args);
    }
}
