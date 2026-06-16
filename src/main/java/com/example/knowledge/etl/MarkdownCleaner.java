package com.example.knowledge.etl;

import org.springframework.stereotype.Component;

@Component
public class MarkdownCleaner {

    /**
     * 去除 Markdown 中的干扰内容
     */
    public String clean(String content) {
        return content
            .replaceAll("(?s)^---\\n.*?\\n---\\n", "")   // YAML frontmatter
            .replaceAll("<[^>]+>", "")                     // HTML 标签
            .replaceAll("!\\[[^]]*]\\([^)]+\\)", "")       // 图片引用
            .replaceAll("\\[([^]]*)]\\([^)]+\\)", "$1")    // 链接只保留文本
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }
}
