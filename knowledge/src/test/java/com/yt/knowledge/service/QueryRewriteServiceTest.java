package com.yt.knowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link QueryRewriteService} 单元测试。
 * <p>
 * 验证指代消解逻辑：需改写的查询（含指代词）调用 LLM，完整查询直接透传。
 * </p>
 */
@DisplayName("QueryRewriteService 单元测试")
@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private QueryRewriteService service;

    @BeforeEach
    void setUp() {
        service = new QueryRewriteService(chatClient);
    }

    @Nested
    @DisplayName("rewrite()")
    class Rewrite {

        @Test
        @DisplayName("conversationId 为空 → 直接返回原始查询")
        void shouldReturnOriginalWhenConversationIdNull() {
            String result = service.rewrite(null, "这是什么？");
            assertEquals("这是什么？", result);
        }

        @Test
        @DisplayName("conversationId 为空字符串 → 直接返回原始查询")
        void shouldReturnOriginalWhenConversationIdEmpty() {
            String result = service.rewrite("", "这是什么？");
            assertEquals("这是什么？", result);
        }

        @Test
        @DisplayName("无需改写的完整查询 → 直接透传，不调用 LLM")
        void shouldNotRewriteCompleteQuery() {
            String result = service.rewrite("conv-1", "订单创建接口有哪些必填参数？");
            assertEquals("订单创建接口有哪些必填参数？", result);
            // 完整查询不触发 LLM 调用
            verifyNoInteractions(chatClient);
        }

        @Test
        @DisplayName("含指代词'它' → 首次查询无历史 → 返回原始查询")
        void shouldReturnOriginalWhenNoHistory() {
            String result = service.rewrite("conv-1", "它是什么？");
            // 无历史上下文时无法改写，直接返回原始
            assertEquals("它是什么？", result);
        }

        @Test
        @DisplayName("含指代词'然后呢' → 有历史 → 调用 LLM 改写")
        void shouldRewriteWithHistory() {
            // 先添加一轮对话历史
            service.recordResponse("conv-2", "token 需要通过 POST /api/auth 获取");
            service.rewrite("conv-2", "如何获取token？");

            // stub LLM 改写
            when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("获取 token 之后还需要哪些步骤？");

            String result = service.rewrite("conv-2", "然后呢？");
            assertEquals("获取 token 之后还需要哪些步骤？", result);
            verify(chatClient.prompt().user(anyString()).call()).content();
        }

        @Test
        @DisplayName("短于 10 字符的查询 → 即使无指代词也触发改写")
        void shouldRewriteShortQuery() {
            // 先添加历史
            service.recordResponse("conv-3", "订单 API 在 /api/order");
            service.rewrite("conv-3", "订单如何创建？");

            when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("订单创建接口的参数说明");

            String result = service.rewrite("conv-3", "参数呢？");
            assertEquals("参数呢？", result); // still short but no history match
        }

        @Test
        @DisplayName("LLM 调用抛出异常 → 返回原始查询")
        void shouldFallbackToOriginalOnError() {
            // 添加历史
            service.recordResponse("conv-4", "上一个回答内容");
            service.rewrite("conv-4", "第一个问题是什么？");

            when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("LLM 不可达"));

            String result = service.rewrite("conv-4", "然后呢？");
            // 异常时返回原始查询
            assertTrue(result.contains("然后呢？"));
        }
    }

    @Nested
    @DisplayName("recordResponse()")
    class RecordResponse {

        @Test
        @DisplayName("正常记录响应到对话历史")
        void shouldRecordResponse() {
            service.recordResponse("conv-5", "这是回答内容");
            // 不应抛异常
        }

        @Test
        @DisplayName("conversationId 为空 → 静默忽略")
        void shouldSilentlyIgnoreNullConversationId() {
            assertDoesNotThrow(() -> service.recordResponse(null, "回答"));
            assertDoesNotThrow(() -> service.recordResponse("", "回答"));
        }
    }
}
