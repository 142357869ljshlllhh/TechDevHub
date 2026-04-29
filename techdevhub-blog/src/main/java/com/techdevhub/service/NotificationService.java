package com.techdevhub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {
    @Async("asyncExecutor")
    public void notifyFollowerAsync(String message){

    }
}
