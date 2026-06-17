package com.yt.knowledge.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DocumentCleaner} 单元测试。
 * <p>
 * 验证文档清洗效果：页码移除、版权声明、纯数字行、空白压缩、碎片过滤。
 * </p>
 */
@DisplayName("DocumentCleaner 单元测试")
class DocumentCleanerTest {

    private DocumentCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new DocumentCleaner();
    }

    @Nested
    @DisplayName("页码移除")
    class PageNumberRemoval {

        @Test
        @DisplayName("'第 1 页' → 被移除")
        void shouldRemoveChinesePageNumber() {
            Document doc = new Document("这是第 1 页的内容，包含足够长度的文本以确保不被过滤");
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertFalse(result.get(0).getText().contains("第"));
            assertTrue(result.get(0).getText().contains("内容"));
        }

        @Test
        @DisplayName("'第 5 页 / 共 10 页' → 被移除")
        void shouldRemovePageWithTotal() {
            Document doc = new Document("第 5 页 / 共 10 页 接口文档说明，需要更多文字来达到最低长度要求");
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertFalse(result.get(0).getText().contains("页"));
            assertTrue(result.get(0).getText().contains("接口文档说明"));
        }

        @Test
        @DisplayName("'Page 1 of 5' → 不受影响（英文页码不处理）")
        void shouldNotAffectEnglishPageNumber() {
            Document doc = new Document("Page 1 of 5 Introduction to the API documentation guide");
            List<Document> result = cleaner.clean(List.of(doc));
            // 英文页码不在清洗规则范围内
            assertTrue(result.get(0).getText().contains("Page"));
        }
    }

    @Nested
    @DisplayName("版权声明移除")
    class CopyrightRemoval {

        @Test
        @DisplayName("'Copyright © 2024' → 整行被移除，剩余内容保留")
        void shouldRemoveCopyrightLine() {
            // 内容足够长（>=30字符），不会被长度过滤器过滤
            Document doc = new Document(
                "Copyright © 2024 My Company. All rights reserved. 实际文档内容从这里开始需要足够长度");
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertFalse(result.get(0).getText().contains("Copyright"));
            assertTrue(result.get(0).getText().contains("实际文档内容"));
        }

        @Test
        @DisplayName("'All rights reserved.' → 被移除，剩余内容保留")
        void shouldRemoveAllRightsReserved() {
            Document doc = new Document(
                "Some content here. All rights reserved. More content that makes this text long enough for test");
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertFalse(result.get(0).getText().toLowerCase().contains("all rights reserved"));
            assertTrue(result.get(0).getText().contains("Some content"));
            assertTrue(result.get(0).getText().contains("More content"));
        }

        @Test
        @DisplayName("纯版权声明无其他内容 → 清洗后为空 → 被长度过滤")
        void shouldFilterPureCopyrightWithoutOtherContent() {
            Document doc = new Document("Copyright © 2024 My Company. All rights reserved.");
            List<Document> result = cleaner.clean(List.of(doc));
            // 版权移除后内容为空/不足30字符，被过滤
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("纯数字行移除")
    class PureNumberLineRemoval {

        @Test
        @DisplayName("独立数字行 '42' → 被移除")
        void shouldRemoveStandalonePageNumber() {
            Document doc = new Document("42\n这是正式文档内容，标题和正文部分需要足够长来通过过滤");
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertFalse(result.get(0).getText().contains("42"));
            assertTrue(result.get(0).getText().contains("正式文档内容"));
        }

        @Test
        @DisplayName("三位数页号 '123' → 被移除")
        void shouldRemoveThreeDigitPageNumber() {
            Document doc = new Document("123 正文内容开始，这里需要更多的文字才能达到最低三十个字符的长度要求");
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertFalse(result.get(0).getText().contains("123"));
            assertTrue(result.get(0).getText().contains("正文内容"));
        }

        @Test
        @DisplayName("四位数 '2024' → 不受影响（只匹配 1~3 位）")
        void shouldNotAffectFourDigitNumber() {
            Document doc = new Document("年份 2024 是一个重要的标志，这里添加更多文字以满足三十字符的长度要求");
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertTrue(result.get(0).getText().contains("2024"));
        }
    }

    @Nested
    @DisplayName("空白压缩")
    class WhitespaceCompression {

        @Test
        @DisplayName("多个空格 → 压缩为单个空格")
        void shouldCompressMultipleSpaces() {
            Document doc = new Document("hello    world     foo  and some more content here");
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertEquals("hello world foo and some more content here", result.get(0).getText());
        }

        @Test
        @DisplayName("换行符 → 替换为空格")
        void shouldReplaceNewlinesWithSpace() {
            Document doc = new Document("line1\nline2\nline3 and more to reach the minimum length for testing");
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertTrue(result.get(0).getText().startsWith("line1 line2 line3"));
        }
    }

    @Nested
    @DisplayName("长度过滤")
    class LengthFilter {

        @Test
        @DisplayName("短于 30 字符的文档 → 被过滤")
        void shouldFilterShortDocument() {
            Document doc = new Document("short text");
            List<Document> result = cleaner.clean(List.of(doc));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("恰好 30 字符 → 保留")
        void shouldKeepDocumentWithMinLength() {
            // 构造恰好 30 字符的内容（不含多余空白）
            String text = "123456789012345678901234567890";  // 30 chars
            Document doc = new Document(text);
            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("空白文档 → 被过滤")
        void shouldFilterBlankDocument() {
            Document doc = new Document("   ");
            List<Document> result = cleaner.clean(List.of(doc));
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("元数据保留")
    class MetadataPreservation {

        @Test
        @DisplayName("清洗后原始元数据保持不变")
        void shouldPreserveOriginalMetadata() {
            Document doc = new Document("这是一段有意义的内容，足够长度达到30字符");
            doc.getMetadata().put("source", "/path/to/test.md");
            doc.getMetadata().put("author", "test");

            List<Document> result = cleaner.clean(List.of(doc));
            assertEquals(1, result.size());
            assertEquals("/path/to/test.md", result.get(0).getMetadata().get("source"));
            assertEquals("test", result.get(0).getMetadata().get("author"));
        }
    }
}
