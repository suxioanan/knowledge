package com.yt.knowledge.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MarkdownCleaner} 单元测试。
 * <p>
 * 验证 Markdown 清洗效果：YAML frontmatter、HTML 标签、图片、链接、换行压缩。
 * </p>
 */
@DisplayName("MarkdownCleaner 单元测试")
class MarkdownCleanerTest {

    private MarkdownCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new MarkdownCleaner();
    }

    @Nested
    @DisplayName("YAML Frontmatter 移除")
    class YamlFrontmatter {

        @Test
        @DisplayName("标准 YAML frontmatter → 被移除")
        void shouldRemoveYamlFrontmatter() {
            String input = """
                ---
                title: 测试文档
                date: 2024-01-01
                author: test
                ---
                这是正文内容。""";
            String result = cleaner.clean(input);
            assertFalse(result.contains("---"));
            assertFalse(result.contains("title:"));
            assertTrue(result.contains("这是正文内容"));
        }

        @Test
        @DisplayName("无 frontmatter 的文档 → 不受影响")
        void shouldNotAffectDocumentWithoutFrontmatter() {
            String input = "# 标题\n这是正文内容。";
            String result = cleaner.clean(input);
            assertTrue(result.contains("# 标题"));
            assertTrue(result.contains("正文内容"));
        }
    }

    @Nested
    @DisplayName("HTML 标签移除")
    class HtmlTags {

        @Test
        @DisplayName("<div>/<span>/<br> → 被移除")
        void shouldRemoveHtmlTags() {
            String input = "这是<div>一段</div><span>文字</span><br/>后面";
            String result = cleaner.clean(input);
            assertFalse(result.contains("<div>"));
            assertFalse(result.contains("<span>"));
            assertTrue(result.contains("一段"));
            assertTrue(result.contains("文字"));
        }

        @Test
        @DisplayName("带属性的 HTML 标签 → 被移除")
        void shouldRemoveHtmlTagsWithAttributes() {
            String input = "<a href=\"http://example.com\">链接文本</a>";
            String result = cleaner.clean(input);
            assertFalse(result.contains("<a"));
            assertTrue(result.contains("链接文本"));
        }
    }

    @Nested
    @DisplayName("图片引用处理")
    class ImageReferences {

        @Test
        @DisplayName("![alt](url) → 整个移除")
        void shouldRemoveImageReference() {
            String input = "这是描述 ![架构图](./arch.png) 后面内容";
            String result = cleaner.clean(input);
            assertFalse(result.contains("架构图"));
            assertFalse(result.contains("./arch.png"));
            assertFalse(result.contains("!"));
            assertTrue(result.contains("这是描述"));
            assertTrue(result.contains("后面内容"));
        }
    }

    @Nested
    @DisplayName("链接处理")
    class Links {

        @Test
        @DisplayName("[text](url) → 保留 text")
        void shouldKeepLinkTextOnly() {
            String input = "请参考 [API 文档](http://example.com/api) 获取详情";
            String result = cleaner.clean(input);
            assertTrue(result.contains("API 文档"));
            assertFalse(result.contains("http://example.com"));
            assertFalse(result.contains("]("));
        }

        @Test
        @DisplayName("多个链接 → 各自保留文本")
        void shouldHandleMultipleLinks() {
            String input = "[文档1](url1) 和 [文档2](url2)";
            String result = cleaner.clean(input);
            assertTrue(result.contains("文档1"));
            assertTrue(result.contains("文档2"));
            assertFalse(result.contains("url1"));
            assertFalse(result.contains("url2"));
        }
    }

    @Nested
    @DisplayName("换行压缩")
    class NewlineCompression {

        @Test
        @DisplayName("3 个以上连续换行 → 压缩为 2 个")
        void shouldCompressExcessiveNewlines() {
            String input = "段落1\n\n\n\n\n段落2";
            String result = cleaner.clean(input);
            // 最多保留 2 个换行（即 1 个空行）
            assertFalse(result.contains("\n\n\n"));
        }

        @Test
        @DisplayName("正常段落间距 → 保持不变")
        void shouldKeepNormalParagraphSpacing() {
            String input = "段落1\n\n段落2";
            String result = cleaner.clean(input);
            assertTrue(result.contains("\n\n"));
        }
    }

    @Nested
    @DisplayName("综合场景")
    class Comprehensive {

        @Test
        @DisplayName("完整 Markdown 文档 → 正确清洗")
        void shouldHandleFullMarkdownDocument() {
            String input = """
                ---
                title: 接口文档
                date: 2024-06-01
                ---
                # API 概述
                本文档描述了 <strong>订单接口</strong> 的使用方式。
                ![流程图](./flow.png)
                详细信息请参考 [订单 API](./api/order.md)。

                创建订单使用 POST 方法，参数包括 userId 和 productId。""";

            String result = cleaner.clean(input);

            // 不应包含 frontmatter
            assertFalse(result.contains("---"));
            assertFalse(result.contains("title:"));
            // 不应包含 HTML
            assertFalse(result.contains("<strong>"));
            assertTrue(result.contains("订单接口"));
            // 不应包含图片
            assertFalse(result.contains("flow.png"));
            // 链接只保留文本
            assertTrue(result.contains("订单 API"));
            assertFalse(result.contains("./api/order.md"));
            // 正文保留
            assertTrue(result.contains("POST"));
            assertTrue(result.contains("userId"));
        }
    }
}
