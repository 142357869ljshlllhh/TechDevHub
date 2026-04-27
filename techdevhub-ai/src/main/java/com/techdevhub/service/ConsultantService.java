package com.techdevhub.service;

import com.techdevhub.vo.AiChatResponse;

public interface ConsultantService {

    AiChatResponse chat(Long userId, String message);

    void clearMemory(Long userId);
}
