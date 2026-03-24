package com.techdevhub.repository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String AI_CHAT_MEMORY_KEY = "ai:chat:memory:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String value = stringRedisTemplate.opsForValue().get(buildKey(memoryId));
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return ChatMessageDeserializer.messagesFromJson(value);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        stringRedisTemplate.opsForValue().set(buildKey(memoryId), ChatMessageSerializer.messagesToJson(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(buildKey(memoryId));
    }

    private String buildKey(Object memoryId) {
        return AI_CHAT_MEMORY_KEY + memoryId;
    }
}
