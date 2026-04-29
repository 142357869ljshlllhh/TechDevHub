package com.techdevhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class TechdevhubAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechdevhubAiApplication.class, args);
    }

}
