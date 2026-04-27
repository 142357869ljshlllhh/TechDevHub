package com.techdevhub.vo;

public class AiChatResponse {

    private String answer;
    private Long memoryId;

    public AiChatResponse(String answer, Long memoryId) {
        this.answer = answer;
        this.memoryId = memoryId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }
}
