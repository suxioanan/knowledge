package com.yt.knowledge.service;

import com.yt.knowledge.etl.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 多轮对话问答服务。
 * <p>
 * 通过 {@code MessageChatMemoryAdvisor} 保持会话上下文，
 * 不同会话通过 {@code conversationId} 区分，自动维护各自的历史消息（最多 10 条）。
 * </p>
 *
 * <p>
 * 使用方式：前端生成唯一 conversationId，后续对话传入相同的 ID 即可维持上下文。
 * 典型场景："XX接口怎么调用？" → "有示例吗？" → "Python版本呢？"
 * </p>
 */
@Service
@RequiredArgsConstructor
public class MultiTurnQAService {

    private final ChatClient chatClient;
    private final InputSanitizer sanitizer;

    /**
     * 多轮对话（阻塞式）。
     * <p>
     * 通过 {@code chat_memory_conversation_id} 参数告诉 Advisor 使用哪个会话的历史。
     * </p>
     *
     * @param conversationId 会话唯一标识（前端生成并维护）
     * @param question       用户当前问题
     * @return LLM 生成的回答
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
     * 多轮对话（流式 SSE）。
     * <p>
     * 与阻塞式逻辑相同，但通过 Flux 流式返回。
     * </p>
     *
     * @param conversationId 会话唯一标识
     * @param question       用户当前问题
     * @return 流式回答的 Flux
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
