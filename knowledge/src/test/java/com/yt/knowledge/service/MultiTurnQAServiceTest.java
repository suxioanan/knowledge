package com.yt.knowledge.service;

import com.yt.knowledge.etl.InputSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * 通过 ArgumentCaptor 验证 advisors Consumer 正确传入了 conversationId。
 * </p>
 */
@DisplayName("MultiTurnQAService 单元测试")
@ExtendWith(MockitoExtension.class)
class MultiTurnQAServiceTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private QueryRewriteService queryRewriteService;

    private InputSanitizer sanitizer;
    private MultiTurnQAService service;

    @BeforeEach
    void setUp() {
        sanitizer = new InputSanitizer();
        service = new MultiTurnQAService(chatClient, sanitizer, queryRewriteService);
    }

    @Nested
    @DisplayName("ask() 多轮对话阻塞式")
    class BlockingAsk {

        @Test
        @DisplayName("正常调用 → QueryRewrite + advisors Consumer 传入 conversationId")
        void shouldPassConversationIdToAdvisor() {
            String conversationId = "conv-abc-123";
            String question = "然后呢？";

            // stub queryRewriteService
            when(queryRewriteService.rewrite(conversationId, question))
                .thenReturn("改写后的查询");

            // 使用 ArgumentCaptor 捕获 advisors 的 Consumer 参数
            // 需要 mock 中间层以启用 capture
            ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callSpec =
                mock(ChatClient.CallResponseSpec.class);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> advisorCaptor =
                ArgumentCaptor.forClass(Consumer.class);
            when(requestSpec.advisors(advisorCaptor.capture())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);
            when(callSpec.content()).thenReturn("你需要先获取 token");

            String result = service.ask(conversationId, question);

            // 验证返回结果
            assertNotNull(result);
            assertTrue(result.contains("token"));

            // 验证 queryRewriteService 被调用
            verify(queryRewriteService).rewrite(conversationId, question);
            verify(queryRewriteService).recordResponse(eq(conversationId), anyString());

            // 验证 advisors Consumer 设置了正确的 conversationId
            ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
            when(advisorSpec.param(anyString(), any())).thenReturn(advisorSpec);
            advisorCaptor.getValue().accept(advisorSpec);
            verify(advisorSpec).param("chat_memory_conversation_id", conversationId);
        }

        @Test
        @DisplayName("不同 conversationId → 各自独立上下文，QueryRewrite 各调用一次")
        void shouldSupportMultipleConversations() {
            ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callSpec =
                mock(ChatClient.CallResponseSpec.class);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);
            when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);
            when(callSpec.content())
                .thenReturn("回答1")
                .thenReturn("回答2");

            when(queryRewriteService.rewrite(anyString(), anyString()))
                .thenReturn("改写1")
                .thenReturn("改写2");

            String result1 = service.ask("conv-1", "问题1");
            String result2 = service.ask("conv-2", "问题1");

            assertEquals("回答1", result1);
            assertEquals("回答2", result2);
            verify(queryRewriteService).rewrite("conv-1", "问题1");
            verify(queryRewriteService).rewrite("conv-2", "问题1");
            verify(queryRewriteService).recordResponse(eq("conv-1"), anyString());
            verify(queryRewriteService).recordResponse(eq("conv-2"), anyString());
        }
    }

    @Nested
    @DisplayName("askStream() 多轮对话流式")
    class StreamingAsk {

        @Test
        @DisplayName("流式返回 → Flux 中每个 Token 正确，QueryRewrite 被调用")
        void shouldReturnStreamingTokens() {
            String conversationId = "conv-1";
            String question = "如何验证？";

            when(queryRewriteService.rewrite(conversationId, question))
                .thenReturn("改写后的查询");

            Flux<String> mockFlux = Flux.just("token", "验证", "通过");
            when(chatClient.prompt()
                    .user(anyString())
                    .advisors(any(Consumer.class))
                    .stream()
                    .content())
                .thenReturn(mockFlux);

            Flux<String> result = service.askStream(conversationId, question);

            List<String> tokens = result.collectList().block();
            assertNotNull(tokens);
            assertEquals(3, tokens.size());
            assertEquals("token", tokens.get(0));
            assertEquals("通过", tokens.get(2));
            verify(queryRewriteService).rewrite(conversationId, question);
        }
    }
}
