package com.techdevhub.comment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.techdevhub")
public class TechdevhubCommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechdevhubCommentApplication.class, args);
    }
}
