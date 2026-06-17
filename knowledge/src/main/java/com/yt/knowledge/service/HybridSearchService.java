package com.yt.knowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final VectorStore vectorStore;

    @Value("${app.retrieval.top-k:5}")
    private int topK;

    @Value("${app.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;

    /**
     * 混合检索：向量检索 + 关键词匹配
     *
     * @param query 查询文本
     * @param finalTopK 最终返回数量
     * @return 融合排序后的文档列表
     */
    public List<Document> hybridSearch(String query, int finalTopK) {
        long start = System.currentTimeMillis();

        // 1. Dense向量检索（召回更多候选）
        List<Document> denseResults = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(finalTopK * 2)  // 召回2倍候选
                .similarityThreshold(similarityThreshold * 0.9)  // 略降低阈值
                .build()
        );

        log.debug("Dense检索结果: {} 条", denseResults.size());

        // 2. 提取关键词进行BM25风格过滤（简化实现）
        List<Document> keywordResults = keywordFilter(query, denseResults);
        log.debug("关键词过滤结果: {} 条", keywordResults.size());

        // 3. RRF融合排序
        List<Document> merged = rrfMerge(denseResults, keywordResults, finalTopK);

        long duration = System.currentTimeMillis() - start;
        log.info("混合检索完成: {}ms, 返回{}条", duration, merged.size());

        return merged;
    }

    /**
     * 关键词过滤（简化版BM25）
     * 从向量检索结果中筛选包含关键词的文档
     */
    private List<Document> keywordFilter(String query, List<Document> candidates) {
        // 提取查询关键词（简单分词：按空格和标点分割）
        Set<String> keywords = extractKeywords(query);

        if (keywords.isEmpty()) {
            return candidates;
        }

        return candidates.stream()
            .filter(doc -> {
                String text = doc.getText().toLowerCase();
                // 计算关键词匹配度
                long matchCount = keywords.stream()
                    .filter(keyword -> text.contains(keyword.toLowerCase()))
                    .count();
                return matchCount > 0;  // 至少匹配一个关键词
            })
            .sorted((a, b) -> {
                // 按匹配关键词数量排序
                String textA = a.getText().toLowerCase();
                String textB = b.getText().toLowerCase();

                long countA = keywords.stream()
                    .filter(k -> textA.contains(k.toLowerCase()))
                    .count();
                long countB = keywords.stream()
                    .filter(k -> textB.contains(k.toLowerCase()))
                    .count();

                return Long.compare(countB, countA);  // 降序
            })
            .toList();
    }

    /**
     * 提取关键词
     */
    // 中文停用词，每次请求复用
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
        "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这");

    private Set<String> extractKeywords(String query) {
        return Arrays.stream(query.split("[\\s\\p{Punct}]+"))
            .filter(word -> word.length() > 1)
            .filter(word -> !STOP_WORDS.contains(word))
            .collect(Collectors.toSet());
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合排序
     *
     * 公式: score = Σ(1 / (k + rank_i))
     * k通常取60
     */
    private List<Document> rrfMerge(List<Document> denseList, List<Document> keywordList, int topK) {
        int k = 60;  // RRF参数
        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        // Dense检索分数
        for (int i = 0; i < denseList.size(); i++) {
            String docId = denseList.get(i).getId();
            double score = 1.0 / (k + i + 1);
            scores.merge(docId, score, Double::sum);
            docMap.put(docId, denseList.get(i));
        }

        // Keyword检索分数
        for (int i = 0; i < keywordList.size(); i++) {
            String docId = keywordList.get(i).getId();
            double score = 1.0 / (k + i + 1);
            scores.merge(docId, score, Double::sum);
            docMap.put(docId, keywordList.get(i));
        }

        // 按RRF分数排序
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> docMap.get(entry.getKey()))
                .filter(Objects::nonNull)  // 过滤可能的null值
                .collect(Collectors.toList());
    }
}
