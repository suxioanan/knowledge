package com.yt.knowledge.etl;

import org.springframework.stereotype.Component;

/**
 * Markdown 文档清洗器。
 * <p>
 * 去除 Markdown 中的干扰内容，保留纯文本语义信息：
 * </p>
 * <ol>
 *   <li>移除 YAML frontmatter（{@code --- ... ---}）</li>
 *   <li>移除内嵌 HTML 标签</li>
 *   <li>移除图片引用（{@code ![alt](url)}），图片对文本检索无意义</li>
 *   <li>保留链接文本，丢弃 URL（{@code [text](url) → text}）</li>
 *   <li>压缩连续空行为最多 2 行</li>
 * </ol>
 */
@Component
public class MarkdownCleaner {

    /**
     * 清洗 Markdown 内容。
     *
     * @param content 原始 Markdown 文本
     * @return 清洗后的纯文本
     */
    public String clean(String content) {
        return content
            .replaceAll("(?s)^---\\n.*?\\n---\\n", "")   // YAML frontmatter
            .replaceAll("<[^>]+>", "")                     // HTML 标签
            .replaceAll("!\\[[^]]*]\\([^)]+\\)", "")       // 图片引用 → 移除
            .replaceAll("\\[([^]]*)]\\([^)]+\\)", "$1")    // 链接 → 保留文本
            .replaceAll("\\n{3,}", "\n\n")                 // 压缩多余换行
            .trim();
    }
}
