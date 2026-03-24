package com.techdevhub.service;

import com.techdevhub.vo.AiChatResponse;

@dev.langchain4j.service.spring.AiService
public interface ConsultantService {

    AiChatResponse chat(Long userId, String message);

    void clearMemory(Long userId);
}
