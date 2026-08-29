package com.techdevhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
// 审核/ingest 是 30s 级 LLM 耗时操作，必须异步在 asyncExecutor 池执行，
// 不能占用 Tomcat 工作线程（否则高并发发布会拖垮整个服务）
@EnableAsync
public class TechdevhubBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechdevhubBlogApplication.class, args);
    }

}
