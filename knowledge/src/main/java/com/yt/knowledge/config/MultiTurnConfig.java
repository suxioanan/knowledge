package com.yt.knowledge.config;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多轮对话支持。
 * ChatClient 由 RagConfig 统一创建，此处仅提供 ChatMemory + Advisor 组件。
 * 使用时通过 .advisors(a -> a.param("chat_memory_conversation_id", conversationId)) 动态传入会话 ID。
 */
@Configuration
public class MultiTurnConfig {

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(10)
            .build();
    }

    @Bean
    public MessageChatMemoryAdvisor chatMemoryAdvisor(ChatMemory chatMemory) {
        // 不设置默认 conversationId，由调用方动态传入
        // 避免不同会话之间的历史消息污染
        return MessageChatMemoryAdvisor.builder(chatMemory)
            .build();
    }
}
