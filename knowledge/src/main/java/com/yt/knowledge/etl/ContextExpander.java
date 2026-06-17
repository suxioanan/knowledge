package com.yt.knowledge.etl;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 上下文扩展器。
 * <p>
 * 在向量检索后，根据检索结果的 chunk_index 元数据，批量拉取同一文件中相邻的 Chunk，
 * 以增强 LLM 生成回答时的上下文完整性。
 * </p>
 */
@Component
public class ContextExpander {

    public List<Document> expandWithNeighbors(List<Document> retrievedChunks,
                                               VectorStore vectorStore,
                                               int neighbors) {
        if (retrievedChunks.isEmpty() || neighbors < 1) {
            return new ArrayList<>(retrievedChunks);
        }

        // 1. 收集所有需要查找的 (source, chunk_index) 对
        Set<String> targetKeys = new HashSet<>();
        Map<String, Document> resultMap = new LinkedHashMap<>();

        for (Document chunk : retrievedChunks) {
            resultMap.put(chunk.getId(), chunk);

            String source = (String) chunk.getMetadata().get("source");
            int index = (int) chunk.getMetadata().getOrDefault("chunk_index", -1);
            if (source == null || index < 0) continue;

            for (int offset = -neighbors; offset <= neighbors; offset++) {
                if (offset == 0) continue;
                int neighborIdx = index + offset;
                if (neighborIdx < 0) continue;
                targetKeys.add(source + "::" + neighborIdx);
            }
        }

        if (targetKeys.isEmpty()) {
            return new ArrayList<>(resultMap.values());
        }

        // 2. 构建 OR 表达式：一次性批量查询所有邻居 Chunk
        // Filter.Expression 是二元操作，需要嵌套：OR(OR(A, B), C)
        List<Filter.Expression> conditions = targetKeys.stream()
            .map(key -> {
                String[] parts = key.split("::", 2);
                Filter.Expression sourceEq = new Filter.Expression(
                    ExpressionType.EQ, new Filter.Key("source"), new Filter.Value(parts[0]));
                Filter.Expression idxEq = new Filter.Expression(
                    ExpressionType.EQ, new Filter.Key("chunk_index"), new Filter.Value(Integer.parseInt(parts[1])));
                return new Filter.Expression(ExpressionType.AND, sourceEq, idxEq);
            })
            .collect(Collectors.toList());

        Filter.Expression combinedFilter = conditions.getFirst();
        for (int i = 1; i < conditions.size(); i++) {
            combinedFilter = new Filter.Expression(ExpressionType.OR, combinedFilter, conditions.get(i));
        }

        // 3. 一次查询返回所有邻居（query 用空串，Qdrant 会按 filter 精确匹配）
        SearchRequest req = SearchRequest.builder()
            .query("")
            .filterExpression(combinedFilter)
            .topK(targetKeys.size())
            .build();
        List<Document> neighborDocs = vectorStore.similaritySearch(req);

        // 4. 合并去重
        for (Document doc : neighborDocs) {
            resultMap.putIfAbsent(doc.getId(), doc);
        }

        return new ArrayList<>(resultMap.values());
    }
}
