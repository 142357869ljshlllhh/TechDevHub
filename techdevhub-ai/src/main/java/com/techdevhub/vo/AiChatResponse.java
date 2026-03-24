package com.techdevhub.vo;

import reactor.core.publisher.Flux;

public class AiChatResponse {

    private Flux<String> answer;
    private Long memoryId;

    public AiChatResponse(Flux<String> answer, Long memoryId) {
        this.answer = answer;
        this.memoryId = memoryId;
    }

    public Flux<String> getAnswer() {
        return answer;
    }

    public void setAnswer(Flux<String> answer) {
        this.answer = answer;
    }

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }
}
