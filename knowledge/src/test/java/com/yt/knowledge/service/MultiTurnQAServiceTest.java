package com.yt.knowledge.service;

import com.yt.knowledge.etl.InputSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link MultiTurnQAService} 单元测试（Mock 依赖）。
 * <p>
 * 使用 RETURNS_DEEP_STUBS 简化 ChatClient 链式调用的 mock。
 * advisors(Consumer) 参数使用 any(Consumer.class) 匹配。
 * </p>
 */
@DisplayName("MultiTurnQAService 单元测试")
@ExtendWith(MockitoExtension.class)
class MultiTurnQAServiceTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private InputSanitizer sanitizer;
    private MultiTurnQAService service;

    @BeforeEach
    void setUp() {
        sanitizer = new InputSanitizer();
        service = new MultiTurnQAService(chatClient, sanitizer);
    }

    @Nested
    @DisplayName("ask() 多轮对话阻塞式")
    class BlockingAsk {

        @Test
        @DisplayName("正常调用 → conversationId 被传入 Advisor")
        void shouldPassConversationIdToAdvisor() {
            String conversationId = "conv-abc-123";
            when(chatClient.prompt()
                    .user(anyString())
                    .advisors(any(Consumer.class))
                    .call()
                    .content())
                .thenReturn("你需要先获取 token");

            String result = service.ask(conversationId, "然后呢？");

            assertNotNull(result);
            assertTrue(result.contains("token"));
        }

        @Test
        @DisplayName("不同 conversationId → 各自独立上下文")
        void shouldSupportMultipleConversations() {
            when(chatClient.prompt()
                    .user(anyString())
                    .advisors(any(Consumer.class))
                    .call()
                    .content())
                .thenReturn("回答1")
                .thenReturn("回答2");

            String result1 = service.ask("conv-1", "问题1");
            String result2 = service.ask("conv-2", "问题1");

            assertEquals("回答1", result1);
            assertEquals("回答2", result2);
        }
    }

    @Nested
    @DisplayName("askStream() 多轮对话流式")
    class StreamingAsk {

        @Test
        @DisplayName("流式返回 → Flux 中每个 Token 正确")
        void shouldReturnStreamingTokens() {
            Flux<String> mockFlux = Flux.just("token", "验证", "通过");
            when(chatClient.prompt()
                    .user(anyString())
                    .advisors(any(Consumer.class))
                    .stream()
                    .content())
                .thenReturn(mockFlux);

            Flux<String> result = service.askStream("conv-1", "如何验证？");

            List<String> tokens = result.collectList().block();
            assertNotNull(tokens);
            assertEquals(3, tokens.size());
            assertEquals("token", tokens.get(0));
            assertEquals("通过", tokens.get(2));
        }
    }
}
