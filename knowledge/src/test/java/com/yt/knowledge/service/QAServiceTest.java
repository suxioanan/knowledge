package com.yt.knowledge.service;

import com.yt.knowledge.etl.ContextExpander;
import com.yt.knowledge.etl.InputSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link QAService} 单元测试（Mock 依赖）。
 * <p>
 * 使用 RETURNS_DEEP_STUBS 简化 ChatClient 链式调用的 mock，
 * ContextExpander 使用 mock 以隔离测试。
 * </p>
 */
@DisplayName("QAService 单元测试")
@ExtendWith(MockitoExtension.class)
class QAServiceTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ContextExpander contextExpander;

    private InputSanitizer sanitizer;
    private QAService qaService;

    @BeforeEach
    void setUp() {
        sanitizer = new InputSanitizer();
        qaService = new QAService(chatClient, sanitizer, vectorStore, contextExpander);

        // 注入 @Value 字段
        ReflectionTestUtils.setField(qaService, "topK", 5);
        ReflectionTestUtils.setField(qaService, "similarityThreshold", 0.7);
        ReflectionTestUtils.setField(qaService, "neighborChunks", 1);
    }

    @Nested
    @DisplayName("ask() 标准问答")
    class StandardAsk {

        @Test
        @DisplayName("正常调用 → 返回 LLM 回答")
        void shouldReturnAnswer() {
            when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("订单创建接口使用 POST /api/order");

            String result = qaService.ask("如何创建订单？");

            assertNotNull(result);
            assertTrue(result.contains("POST"));
        }

        @Test
        @DisplayName("空问题 → 被 InputSanitizer 拒绝")
        void shouldRejectEmptyQuestion() {
            assertThrows(IllegalArgumentException.class, () -> qaService.ask(""));
            assertThrows(IllegalArgumentException.class, () -> qaService.ask(null));
            // ChatClient 不应被调用（sanitizer 提前抛异常）
            verifyNoInteractions(chatClient);
        }
    }

    @Nested
    @DisplayName("askStream() 流式问答")
    class StreamAsk {

        @Test
        @DisplayName("正常调用 → 返回 Flux<String>")
        void shouldReturnFlux() {
            Flux<String> mockFlux = Flux.just("订单", "创建", "接口");
            when(chatClient.prompt().user(anyString()).stream().content())
                .thenReturn(mockFlux);

            Flux<String> result = qaService.askStream("如何创建订单？");

            List<String> tokens = result.collectList().block();
            assertNotNull(tokens);
            assertEquals(3, tokens.size());
            assertTrue(tokens.contains("订单"));
        }
    }

    @Nested
    @DisplayName("askWithNeighborContext() 增强问答")
    class EnhancedAsk {

        @Test
        @DisplayName("检索到结果 → 拼接上下文后调用 LLM")
        void shouldRetrieveAndExpandAndGenerate() {
            // 模拟向量检索
            Document doc = new Document("POST /api/order 创建订单",
                Map.of("file_name", "order.md", "chunk_index", 0, "source", "/data/order.md"));
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

            // 模拟 ContextExpander 扩展
            when(contextExpander.expandWithNeighbors(anyList(), eq(vectorStore), eq(1)))
                .thenReturn(List.of(doc));

            // 模拟 ChatClient 链式调用
            when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("订单通过 POST /api/order 创建");

            String result = qaService.askWithNeighborContext("如何创建订单？");

            assertNotNull(result);
            assertTrue(result.contains("order"));
            verify(vectorStore).similaritySearch(any(SearchRequest.class));
            verify(contextExpander).expandWithNeighbors(anyList(), eq(vectorStore), eq(1));
        }

        @Test
        @DisplayName("检索到结果但扩展后为空 → 返回无相关信息提示")
        void shouldHandleExpandedEmpty() {
            Document doc = new Document("POST /api/order 创建订单",
                Map.of("file_name", "order.md"));
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

            // mock expander 返回空列表 → 触发硬编码回复
            when(contextExpander.expandWithNeighbors(anyList(), eq(vectorStore), eq(1)))
                .thenReturn(List.of());

            String result = qaService.askWithNeighborContext("如何创建订单？");

            assertNotNull(result);
            assertTrue(result.contains("未找到相关信息"));
            // 扩展为空时不调用 LLM
            verifyNoInteractions(chatClient);
        }

        @Test
        @DisplayName("检索无结果 → 仍正常调用 LLM（上下文为空）")
        void shouldHandleEmptyRetrieval() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

            // empty list wasn't null, so it goes into expander → returns empty
            when(contextExpander.expandWithNeighbors(anyList(), eq(vectorStore), eq(1)))
                .thenReturn(List.of());

            String result = qaService.askWithNeighborContext("未知问题999abc");

            assertNotNull(result);
            assertTrue(result.contains("未找到相关信息"));
            verify(vectorStore).similaritySearch(any(SearchRequest.class));
            verify(contextExpander).expandWithNeighbors(anyList(), eq(vectorStore), eq(1));
        }
    }
}
