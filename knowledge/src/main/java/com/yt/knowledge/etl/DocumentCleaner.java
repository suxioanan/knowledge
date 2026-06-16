package com.yt.knowledge.etl;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档清洗器。
 * <p>
 * 对原始文档执行以下清洗操作，去除噪声信息：
 * </p>
 * <ol>
 *   <li>移除页码标记（如"第 1 页 / 共 5 页"）</li>
 *   <li>移除版权声明行（Copyright © 20XX ...）</li>
 *   <li>移除独立的纯数字行（通常是页号残留）</li>
 *   <li>合并多余空白字符（多空格、换行 → 单空格）</li>
 * </ol>
 * <p>
 * 清洗后过滤掉空白文档和长度不足 30 字符的碎片。
 * </p>
 */
@Component
public class DocumentCleaner {

    /**
     * 批量清洗文档列表。
     *
     * @param documents 原始文档列表
     * @return 清洗后的文档列表（已过滤空白和过短碎片）
     */
    public List<Document> clean(List<Document> documents) {
        return documents.stream()
            .map(this::cleanDocument)
            .filter(doc -> !doc.getText().isBlank())
            .filter(doc -> doc.getText().length() >= 30)
            .toList();
    }

    /**
     * 清洗单个文档的文本内容。
     * <p>
     * 正则规则：
     * <ul>
     *   <li>{@code 第\s*\d+\s*页} — 中文页码格式，支持"第1页"、"第 5 页 / 共 10 页"</li>
     *   <li>{@code copyright\s*©?\s*20\d{2}.*} — 英文版权声明行</li>
     *   <li>{@code ^\d{1,3}\s*$} — 整行仅 1~3 位数字（页号残留，如"42"）</li>
     *   <li>{@code \s+} — 合并所有连续空白为单个空格，去除首尾空白</li>
     * </ul>
     *
     * @param doc 待清洗的文档
     * @return 清洗后的文档（保留原始元数据）
     */
    private Document cleanDocument(Document doc) {
        String text = doc.getText();

        // 移除中文页码（如"第 1 页"、"第 5 页 / 共 10 页"）
        text = text.replaceAll("(?i)第\\s*\\d+\\s*页(\\s*共\\s*\\d+\\s*页)?", "");
        // 移除版权声明行（Copyright © 2024 XXX）
        text = text.replaceAll("(?i)copyright\\s*©?\\s*20\\d{2}.*", "");
        // 移除残留的 "All rights reserved."
        text = text.replaceAll("(?i)all rights reserved\\.?", "");
        // 移除独立的纯数字行（页号残留）
        text = text.replaceAll("^\\d{1,3}\\s*$", "");
        // 合并多余空白
        text = text.replaceAll("\\s+", " ").trim();

        return new Document(text, doc.getMetadata());
    }
}
