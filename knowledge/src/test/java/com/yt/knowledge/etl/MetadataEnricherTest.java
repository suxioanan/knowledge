package com.yt.knowledge.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MetadataEnricher} 单元测试。
 * <p>
 * 验证 Chunk 元数据注入的正确性。
 * </p>
 */
@DisplayName("MetadataEnricher 单元测试")
class MetadataEnricherTest {

    private MetadataEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new MetadataEnricher();
    }

    @Nested
    @DisplayName("空列表输入")
    class EmptyList {

        @Test
        @DisplayName("空列表 → 返回空列表，不抛异常")
        void shouldHandleEmptyDocumentList() {
            List<Document> result = enricher.enrich(List.of(), "/data/test.md");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("source 和 file_name 元数据")
    class SourceAndFileName {

        @Test
        @DisplayName("文件路径正确注入为 source")
        void shouldSetSourceAsAbsolutePath() {
            List<Document> docs = createDocs("chunk content");
            List<Document> result = enricher.enrich(docs, "/data/docs/api/order.md");

            assertEquals("/data/docs/api/order.md",
                result.get(0).getMetadata().get("source"));
        }

        @Test
        @DisplayName("文件名正确注入为 file_name")
        void shouldSetFileName() {
            List<Document> docs = createDocs("content");
            List<Document> result = enricher.enrich(docs, "/data/docs/api/order.md");

            assertEquals("order.md",
                result.get(0).getMetadata().get("file_name"));
        }
    }

    @Nested
    @DisplayName("chunk_index 和 chunk_total")
    class ChunkIndexAndTotal {

        @Test
        @DisplayName("单个 Chunk → index=0, total=1")
        void shouldHandleSingleChunk() {
            List<Document> docs = createDocs("one chunk");
            List<Document> result = enricher.enrich(docs, "/data/test.txt");

            assertEquals(0, result.get(0).getMetadata().get("chunk_index"));
            assertEquals(1, result.get(0).getMetadata().get("chunk_total"));
        }

        @Test
        @DisplayName("多个 Chunk → 索引递增，total 一致")
        void shouldHandleMultipleChunks() {
            List<Document> docs = createDocs("chunk1", "chunk2", "chunk3");
            List<Document> result = enricher.enrich(docs, "/data/test.txt");

            for (int i = 0; i < 3; i++) {
                assertEquals(i, result.get(i).getMetadata().get("chunk_index"),
                    "chunk_index 应为 " + i);
                assertEquals(3, result.get(i).getMetadata().get("chunk_total"),
                    "chunk_total 应为 3");
            }
        }
    }

    @Nested
    @DisplayName("file_type")
    class FileType {

        @ParameterizedTest
        @CsvSource({
            "/data/doc.pdf, pdf",
            "/data/doc.md, md",
            "/data/doc.txt, txt",
            "/data/doc.docx, docx",
            "/data/doc.DOCX, docx",  // 大写转小写
        })
        @DisplayName("根据文件扩展名正确推断 file_type")
        void shouldInferFileType(String filePath, String expectedType) {
            List<Document> docs = createDocs("content");
            List<Document> result = enricher.enrich(docs, filePath);

            assertEquals(expectedType, result.get(0).getMetadata().get("file_type"));
        }

        @Test
        @DisplayName("无扩展名 → 'unknown'")
        void shouldReturnUnknownForNoExtension() {
            List<Document> docs = createDocs("content");
            List<Document> result = enricher.enrich(docs, "/data/README");

            assertEquals("unknown", result.get(0).getMetadata().get("file_type"));
        }
    }

    @Nested
    @DisplayName("category 分类")
    class Category {

        @ParameterizedTest
        @CsvSource({
            "/data/docs/api/order.md,     api",
            "/data/docs/database/design.md, database",
            "/data/docs/product/overview.md, product",
            "/data/docs/wiki/faq.md,       wiki",
            "/data/docs/other/test.md,     other",
            "/data/其他/test.md,             other",
        })
        @DisplayName("根据路径子目录正确推断 category")
        void shouldGuessCategoryFromPath(String filePath, String expectedCategory) {
            List<Document> docs = createDocs("content");
            List<Document> result = enricher.enrich(docs, filePath);

            assertEquals(expectedCategory, result.get(0).getMetadata().get("category"));
        }
    }

    @Nested
    @DisplayName("doc_id 唯一性")
    class DocIdUniqueness {

        @Test
        @DisplayName("每个 Chunk 的 doc_id 应唯一")
        void shouldHaveUniqueDocId() {
            List<Document> docs = createDocs("chunk1", "chunk2", "chunk3");
            List<Document> result = enricher.enrich(docs, "/data/test.md");

            String id1 = (String) result.get(0).getMetadata().get("doc_id");
            String id2 = (String) result.get(1).getMetadata().get("doc_id");
            String id3 = (String) result.get(2).getMetadata().get("doc_id");

            assertNotNull(id1);
            assertNotNull(id2);
            assertNotNull(id3);
            assertNotEquals(id1, id2);
            assertNotEquals(id2, id3);
            assertNotEquals(id1, id3);
        }

        @Test
        @DisplayName("doc_id 格式为标准 UUID")
        void shouldHaveValidUuidFormat() {
            List<Document> docs = createDocs("content");
            List<Document> result = enricher.enrich(docs, "/data/test.md");

            String docId = (String) result.get(0).getMetadata().get("doc_id");
            // UUID 格式：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
            assertTrue(docId.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        }
    }

    @Nested
    @DisplayName("imported_at")
    class ImportedAt {

        @Test
        @DisplayName("正确注入导入时间")
        void shouldSetImportTimestamp() {
            List<Document> docs = createDocs("content");
            List<Document> result = enricher.enrich(docs, "/data/test.md");

            String importedAt = (String) result.get(0).getMetadata().get("imported_at");
            assertNotNull(importedAt);
            // ISO-8601 格式，应包含日期和 T
            assertTrue(importedAt.contains("T") || importedAt.contains("Z"));
        }

        @Test
        @DisplayName("同一批次的所有 Chunk 导入时间应一致")
        void shouldHaveSameTimestampForBatch() {
            List<Document> docs = createDocs("c1", "c2", "c3");
            List<Document> result = enricher.enrich(docs, "/data/test.md");

            String t1 = (String) result.get(0).getMetadata().get("imported_at");
            String t2 = (String) result.get(1).getMetadata().get("imported_at");
            String t3 = (String) result.get(2).getMetadata().get("imported_at");

            assertEquals(t1, t2);
            assertEquals(t2, t3);
        }
    }

    // ===== 辅助方法 =====

    /** 创建指定内容的 Document 列表 */
    private List<Document> createDocs(String... contents) {
        List<Document> docs = new ArrayList<>();
        for (String content : contents) {
            docs.add(new Document(content));
        }
        return docs;
    }
}
