package com.yt.knowledge.etl;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 元数据富化器。
 * <p>
 * 为每个 Chunk 注入结构化元数据，用于：
 * </p>
 * <ul>
 *   <li><b>检索溯源</b>：通过 {@code source / file_name} 定位原始文档</li>
 *   <li><b>增量同步</b>：通过 {@code source} 精确删除某个文件的所有 Chunk</li>
 *   <li><b>上下文扩展</b>：通过 {@code chunk_index} 找到相邻 Chunk（{@link ContextExpander}）</li>
 *   <li><b>分类统计</b>：通过 {@code category} 进行按分类的检索和指标统计</li>
 *   <li><b>审计追踪</b>：通过 {@code doc_id / imported_at} 追踪导入记录</li>
 * </ul>
 *
 * <h3>注入的元数据字段</h3>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>source</td><td>String</td><td>源文件绝对路径（增量同步的关键字段）</td></tr>
 *   <tr><td>file_name</td><td>String</td><td>文件名（不含路径）</td></tr>
 *   <tr><td>file_type</td><td>String</td><td>文件扩展名（pdf/md/txt 等）</td></tr>
 *   <tr><td>chunk_index</td><td>int</td><td>当前 Chunk 在该文件中的序号（从 0 开始）</td></tr>
 *   <tr><td>chunk_total</td><td>int</td><td>该文件的总 Chunk 数</td></tr>
 *   <tr><td>doc_id</td><td>String</td><td>UUID 唯一标识</td></tr>
 *   <tr><td>imported_at</td><td>String</td><td>导入时间（ISO-8601）</td></tr>
 *   <tr><td>category</td><td>String</td><td>文档分类（api/database/product/wiki/other）</td></tr>
 * </table>
 */
@Component
public class MetadataEnricher {

    /**
     * 为文档 Chunk 列表注入元数据。
     * <p>
     * 调用时机：在 {@link ChunkSplitter#split} 之后、写入 VectorStore 之前。
     * 必须按文件逐个调用，以确保 source 字段正确指向该文件的路径。
     * </p>
     *
     * @param documents  该文件的 Chunk 列表
     * @param sourceFile 源文件的绝对路径
     * @return 注入元数据后的 Chunk 列表（原列表被就地修改）
     */
    public List<Document> enrich(List<Document> documents, String sourceFile) {
        Path path = Path.of(sourceFile);
        String fileName = path.getFileName().toString();
        Instant now = Instant.now();

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            doc.getMetadata().put("source", sourceFile);
            doc.getMetadata().put("file_name", fileName);
            doc.getMetadata().put("file_type", extension(fileName));
            doc.getMetadata().put("chunk_index", i);
            doc.getMetadata().put("chunk_total", documents.size());
            doc.getMetadata().put("doc_id", UUID.randomUUID().toString());
            doc.getMetadata().put("imported_at", now.toString());
            doc.getMetadata().put("category", guessCategory(sourceFile));
        }
        return documents;
    }

    /**
     * 提取文件扩展名。
     *
     * @param filename 文件名
     * @return 小写扩展名（不含点），无扩展名时返回 "unknown"
     */
    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "unknown" : filename.substring(dot + 1).toLowerCase();
    }

    /**
     * 根据文件路径推测文档分类。
     * <p>
     * 分类规则基于 {@code docs/} 目录的子目录名：
     * {@code api → "api", database → "database", product → "product", wiki → "wiki"}，
     * 未匹配则返回 {@code "other"}。
     * </p>
     *
     * @param filePath 文件绝对路径
     * @return 文档分类标签
     */
    private String guessCategory(String filePath) {
        if (filePath.contains("/api/"))       return "api";
        if (filePath.contains("/database/"))  return "database";
        if (filePath.contains("/product/"))   return "product";
        if (filePath.contains("/wiki/"))      return "wiki";
        return "other";
    }
}
