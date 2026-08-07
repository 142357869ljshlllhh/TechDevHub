package com.techdevhub.category;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.techdevhub")
@EnableFeignClients
public class TechdevhubCategoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechdevhubCategoryApplication.class, args);
    }
}

