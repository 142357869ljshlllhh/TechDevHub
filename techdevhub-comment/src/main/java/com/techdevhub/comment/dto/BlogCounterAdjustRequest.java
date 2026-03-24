package com.techdevhub.comment.dto;

public class BlogCounterAdjustRequest {

    private Integer delta;

    public BlogCounterAdjustRequest() {
    }

    public BlogCounterAdjustRequest(Integer delta) {
        this.delta = delta;
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }
}
