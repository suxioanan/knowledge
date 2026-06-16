package com.yt.knowledge.service;

import com.yt.knowledge.etl.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class MultiTurnQAService {

    private final ChatClient chatClient;
    private final InputSanitizer sanitizer;

    /**
     * 多轮对话 — 阻塞式
     */
    public String ask(String conversationId, String question) {
        String cleaned = sanitizer.sanitize(question);
        return chatClient
            .prompt()
            .user(cleaned)
            .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
            .call()
            .content();
    }

    /**
     * 多轮对话 — 流式
     */
    public Flux<String> askStream(String conversationId, String question) {
        String cleaned = sanitizer.sanitize(question);
        return chatClient
            .prompt()
            .user(cleaned)
            .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
            .stream()
            .content();
    }
}
