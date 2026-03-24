package com.techdevhub.config;

import com.techdevhub.repository.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiMemoryProperties.class)
public class AiModuleConfiguration {

    @Bean
    public ChatMemoryProvider chatMemoryProvider(RedisChatMemoryStore redisChatMemoryStore,
                                                 AiMemoryProperties aiMemoryProperties) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(aiMemoryProperties.getMaxMessages())
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }
}
