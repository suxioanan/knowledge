package com.example.knowledge.etl;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContextExpander {

    /**
     * 检索后，根据 chunk_index 元数据扩展相邻上下文
     * 需要 Qdrant 已建立 source 和 chunk_index 的 payload 索引
     */
    public List<Document> expandWithNeighbors(List<Document> retrievedChunks,
                                               VectorStore vectorStore,
                                               int neighbors) {
        List<Document> expanded = new ArrayList<>(retrievedChunks);
        for (Document chunk : retrievedChunks) {
            String source = (String) chunk.getMetadata().get("source");
            int index = (int) chunk.getMetadata().getOrDefault("chunk_index", -1);
            if (source == null || index < 0) continue;

            for (int offset = -neighbors; offset <= neighbors; offset++) {
                if (offset == 0) continue;
                int neighborIdx = index + offset;
                if (neighborIdx < 0) continue;

                SearchRequest req = SearchRequest.builder()
                    .filterExpression(
                        "source == '" + source + "' && chunk_index == " + neighborIdx)
                    .topK(1)
                    .build();
                List<Document> neighborsDocs = vectorStore.similaritySearch(req);
                expanded.addAll(neighborsDocs);
            }
        }
        return expanded.stream().distinct().toList();
    }
}
