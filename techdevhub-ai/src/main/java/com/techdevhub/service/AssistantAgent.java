package com.techdevhub.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService
public interface AssistantAgent {

    Flux<String> chat(@MemoryId Long memoryId, @UserMessage String message);
}
