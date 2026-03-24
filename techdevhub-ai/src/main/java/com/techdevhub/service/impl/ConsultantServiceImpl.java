package com.techdevhub.service.impl;

import com.techdevhub.repository.RedisChatMemoryStore;
import com.techdevhub.service.ConsultantService;
import com.techdevhub.service.AssistantAgent;
import com.techdevhub.vo.AiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@dev.langchain4j.service.spring.AiService
public class ConsultantServiceImpl implements ConsultantService {

    private final AssistantAgent assistantAgent;
    private final RedisChatMemoryStore redisChatMemoryStore;

    @Override
    public AiChatResponse chat(Long userId, String message) {
        Flux<String> answer = assistantAgent.chat(userId, message);
        return new AiChatResponse(answer, userId);
    }

    @Override
    public void clearMemory(Long userId) {
        redisChatMemoryStore.deleteMessages(userId);
    }
}
