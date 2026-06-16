package com.yt.knowledge.service;

import com.yt.knowledge.etl.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class QAService {

    private final ChatClient chatClient;
    private final InputSanitizer sanitizer;

    /**
     * 阻塞式问答
     */
    public String ask(String question) {
        String cleaned = sanitizer.sanitize(question);
        return chatClient
            .prompt()
            .user(cleaned)
            .call()
            .content();
    }

    /**
     * 流式问答 — 返回 Flux<String>，Controller 层通过 SSE 推送
     */
    public Flux<String> askStream(String question) {
        String cleaned = sanitizer.sanitize(question);
        return chatClient
            .prompt()
            .user(cleaned)
            .stream()
            .content();
    }
}
