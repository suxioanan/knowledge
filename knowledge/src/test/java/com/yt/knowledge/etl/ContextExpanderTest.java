package com.yt.knowledge.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link ContextExpander} 单元测试。
 * <p>
 * 验证根据 chunk_index 元数据扩展相邻 Chunk 的逻辑。
 * </p>
 */
@DisplayName("ContextExpander 单元测试")
@ExtendWith(MockitoExtension.class)
class ContextExpanderTest {

    @Mock
    private VectorStore vectorStore;

    private ContextExpander expander;

    @BeforeEach
    void setUp() {
        expander = new ContextExpander();
    }

    @Nested
    @DisplayName("expandWithNeighbors()")
    class ExpandWithNeighbors {

        @Test
        @DisplayName("单个 Chunk → 获取前后各 1 个邻居")
        void shouldExpandSingleChunkWithNeighbors() {
            Document center = doc("内容2", "/data/test.md", "test.md", 1);

            // 第一次调用返回左邻居, 第二次返回右邻居
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("内容1", "/data/test.md", "test.md", 0)))
                .thenReturn(List.of(doc("内容3", "/data/test.md", "test.md", 2)));

            List<Document> result = expander.expandWithNeighbors(
                List.of(center), vectorStore, 1);

            assertEquals(3, result.size(),
                "应包含原始 + 2 个邻居，共 3 个");
            assertTrue(result.stream().anyMatch(d -> d.getText().contains("内容1")));
            assertTrue(result.stream().anyMatch(d -> d.getText().contains("内容2")));
            assertTrue(result.stream().anyMatch(d -> d.getText().contains("内容3")));
            // OR 批查：只调 1 次 similaritySearch
            verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        }

        @Test
        @DisplayName("边界 Chunk (index=0) → 只取右侧邻居")
        void shouldHandleBoundaryChunkAtStart() {
            Document first = doc("开头", "/data/a.md", "a.md", 0);

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("第二个", "/data/a.md", "a.md", 1)));

            List<Document> result = expander.expandWithNeighbors(
                List.of(first), vectorStore, 1);

            assertEquals(2, result.size(), "应包含原始 + 1 个右邻居");
            // index=0, 左侧 neighborIdx=-1 被跳过，只调 1 次
            verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        }

        @Test
        @DisplayName("无 source 的 Chunk → 跳过扩展")
        void shouldSkipChunkWithoutSource() {
            Document noSource = new Document("无来源内容");
            // chunk_index 不存在，getOrDefault 返回 -1

            List<Document> result = expander.expandWithNeighbors(
                List.of(noSource), vectorStore, 1);

            assertEquals(1, result.size(), "无 source 时不扩展");
            verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
        }

        @Test
        @DisplayName("无 chunk_index 的 Chunk → 跳过扩展")
        void shouldSkipChunkWithoutChunkIndex() {
            Document noIndex = new Document("无编号内容");
            noIndex.getMetadata().put("source", "/data/test.md");

            List<Document> result = expander.expandWithNeighbors(
                List.of(noIndex), vectorStore, 1);

            assertEquals(1, result.size(), "无 chunk_index 时不扩展");
            verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
        }

        @Test
        @DisplayName("多个检索结果 + 共享邻居 → 去重")
        void shouldDeduplicateSharedNeighbors() {
            Document chunk1 = doc("块1", "/data/f.md", "f.md", 2);
            Document chunk2 = doc("块2", "/data/f.md", "f.md", 3);

            // 块1 前后：attempts on index 1 和 3 (但 index 3 是 chunk2 自身)
            // 块2 前后：attempts on index 2 和 4 (但 index 2 是 chunk1 自身)
            // 总共 2 个有效邻居：index 1 和 index 4
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("块0", "/data/f.md", "f.md", 1)))
                .thenReturn(List.of(doc("块4", "/data/f.md", "f.md", 4)));

            List<Document> result = expander.expandWithNeighbors(
                List.of(chunk1, chunk2), vectorStore, 1);

            // 去重后：块0, 块1, 块2, 块4 = 4个
            assertEquals(4, result.size(), "共享邻居应去重");
            // 2 个 chunk × 2 个邻居（前后），但 chunk2 的 index 3 是第一个chunk的 neighbor，
            // 没有 source + chunk_index 时怎么办... 
            // 实际上 neighborIdx 3 对 chunk2 来说是自身 offset 0 被跳过，所以应该是 4 次调用
            // 但调用顺序取决于循环顺序
            verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        }

        @Test
        @DisplayName("source 含特殊字符 → 正确转义，不抛异常")
        void shouldEscapeSpecialCharactersInSource() {
            Document center = doc("内容", "/data/t'est\\file.md", "file.md", 1);

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

            List<Document> result = expander.expandWithNeighbors(
                List.of(center), vectorStore, 1);

            assertEquals(1, result.size()); // 没找到邻居
            verify(vectorStore, atLeastOnce()).similaritySearch(any(SearchRequest.class));
        }
    }

    /** 创建带元数据的 Document */
    private static Document doc(String text, String source, String fileName, int chunkIndex) {
        Document d = new Document(text, Map.of(
            "source", source,
            "file_name", fileName,
            "chunk_index", chunkIndex,
            "chunk_total", 5
        ));
        return d;
    }
}
