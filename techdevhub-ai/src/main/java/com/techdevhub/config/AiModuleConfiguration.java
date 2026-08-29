package com.techdevhub.config;

import com.techdevhub.repository.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// 追加 AiPythonProperties：Python 适配层（T0）与旧 langchain4j 配置在此一并启用
@EnableConfigurationProperties({AiMemoryProperties.class, AiPythonProperties.class})
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
