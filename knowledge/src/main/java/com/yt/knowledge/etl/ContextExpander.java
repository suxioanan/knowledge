package com.yt.knowledge.etl;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文扩展器。
 * <p>
 * 在向量检索后，根据检索结果的 chunk_index 元数据，拉取同一文件中相邻的 Chunk，
 * 以增强 LLM 生成回答时的上下文完整性。
 * </p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>对每个检索到的 Chunk，提取其 {@code source} 和 {@code chunk_index}</li>
 *   <li>按偏移量（前后各 N 个）构造精确过滤查询</li>
 *   <li>从 VectorStore 中拉取相邻 Chunk，去重后与原始结果合并</li>
 * </ol>
 *
 * <h3>前置条件</h3>
 * Qdrant 必须已为 {@code source} 和 {@code chunk_index} 建立 payload 索引。
 * 由 Spring AI 的 {@code initialize-schema} 自动处理。
 */
@Component
public class ContextExpander {

    /**
     * 扩展检索结果，添加相邻 Chunk 作为上下文。
     *
     * @param retrievedChunks Top-K 检索结果
     * @param vectorStore     向量存储（用于按过滤条件精确查询相邻 Chunk）
     * @param neighbors       前后各扩展的 Chunk 数量（如 1 = 前后各 1 个）
     * @return 扩展后的 Chunk 列表（已去重）
     */
    public List<Document> expandWithNeighbors(List<Document> retrievedChunks,
                                               VectorStore vectorStore,
                                               int neighbors) {
        List<Document> expanded = new ArrayList<>(retrievedChunks);
        for (Document chunk : retrievedChunks) {
            String source = (String) chunk.getMetadata().get("source");
            int index = (int) chunk.getMetadata().getOrDefault("chunk_index", -1);
            // 跳过无法定位的 Chunk
            if (source == null || index < 0) continue;

            // 遍历偏移范围 [-neighbors, neighbors]，跳过自身（offset=0）
            for (int offset = -neighbors; offset <= neighbors; offset++) {
                if (offset == 0) continue;
                int neighborIdx = index + offset;
                if (neighborIdx < 0) continue;

                // 精确过滤：同一文件 + 指定 chunk_index
                SearchRequest req = SearchRequest.builder()
                    .filterExpression(
                        "source == '" + source + "' && chunk_index == " + neighborIdx)
                    .topK(1)
                    .build();
                List<Document> neighborsDocs = vectorStore.similaritySearch(req);
                expanded.addAll(neighborsDocs);
            }
        }
        // 去重（相邻 Chunk 可能被多个检索结果共享）
        return expanded.stream().distinct().toList();
    }
}
