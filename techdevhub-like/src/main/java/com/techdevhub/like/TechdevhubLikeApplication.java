package com.techdevhub.like;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.techdevhub")
public class TechdevhubLikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechdevhubLikeApplication.class, args);
    }
}
