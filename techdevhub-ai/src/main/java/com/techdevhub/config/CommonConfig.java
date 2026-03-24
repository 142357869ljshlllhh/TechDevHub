package com.techdevhub.config;

import com.techdevhub.service.ConsultantService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfig {
    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Bean
    public ConsultantService aiService() {
        ConsultantService consultantService = AiServices.builder(ConsultantService.class)
                .chatModel(openAiChatModel)
                .build();
        return consultantService;
    }
}
