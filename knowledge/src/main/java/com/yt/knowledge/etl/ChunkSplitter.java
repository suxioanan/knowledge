package com.yt.knowledge.etl;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文本切片器。
 * <p>
 * 将清洗后的文档按 Token 数切分为固定大小的 Chunk，
 * 用于后续 Embedding 和向量检索。
 * </p>
 *
 * <h3>参数说明</h3>
 * <ul>
 *   <li><b>chunkSize=800</b>：每个 Chunk 最大 Token 数，兼顾语义完整性与检索精度</li>
 *   <li><b>minChunkSizeChars=350</b>：最小字符数，过短的 Chunk 缺乏语义信息</li>
 *   <li><b>minChunkLengthToEmbed=50</b>：低于此长度的片段不进行 Embedding（噪声过滤）</li>
 *   <li><b>keepSeparator=true</b>：保留原始段落分隔符，维持文档结构</li>
 * </ul>
 */
@Component
public class ChunkSplitter {

    private final TokenTextSplitter splitter;

    public ChunkSplitter() {
        this.splitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(50)
            .withKeepSeparator(true)
            .build();
    }

    /**
     * 对文档列表执行切片。
     *
     * @param documents 待切片的文档列表（通常来自 {@link DocumentCleaner} 清洗后的结果）
     * @return 切片后的 Chunk 列表，每个 Chunk 继承原文档的元数据
     */
    public List<Document> split(List<Document> documents) {
        return splitter.apply(documents);
    }
}
