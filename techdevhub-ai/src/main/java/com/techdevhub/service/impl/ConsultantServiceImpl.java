package com.techdevhub.service.impl;

import com.techdevhub.repository.RedisChatMemoryStore;
import com.techdevhub.service.AssistantAgent;
import com.techdevhub.service.ConsultantService;
import com.techdevhub.vo.AiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConsultantServiceImpl implements ConsultantService {

    private final AssistantAgent assistantAgent;
    private final RedisChatMemoryStore redisChatMemoryStore;

    @Override
    public AiChatResponse chat(Long userId, String message) {
        String answer = assistantAgent.chat(userId, message)
                .collectList()
                .map(parts -> String.join("", parts))
                .block();
        return new AiChatResponse(answer == null ? "" : answer, userId);
    }

    @Override
    public void clearMemory(Long userId) {
        redisChatMemoryStore.deleteMessages(userId);
    }
}
