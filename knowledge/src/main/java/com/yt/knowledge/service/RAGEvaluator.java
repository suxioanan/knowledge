package com.yt.knowledge.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索质量评估服务。
 * <p>
 * 通过预定义的测试用例评估向量检索质量和 LLM 生成效果：
 * </p>
 *
 * <h3>评估指标</h3>
 * <ul>
 *   <li><b>Top-5 命中率（Top-5 Hit Rate）</b>：Top-5 检索结果中至少有一个来自预期来源文档的比例</li>
 *   <li><b>平均关键词覆盖率（Avg Keyword Coverage）</b>：LLM 回答中覆盖预期关键词的平均比例（0~1）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * POST /api/knowledge/eval
 * Body: [
 *   {
 *     "question": "如何创建订单？",
 *     "expectedSources": ["order.md"],
 *     "expectedKeywords": ["POST", "/api/order", "创建"]
 *   }
 * ]
 * }</pre>
 */
@Service
@RequiredArgsConstructor
public class RAGEvaluator {

    private final VectorStore vectorStore;
    private final QAService qaService;

    /**
     * 执行评估。
     *
     * @param testCases 测试用例列表
     * @return 评估结果（含每个用例的详情和汇总指标）
     */
    public EvalResult evaluate(List<EvalCase> testCases) {
        EvalResult result = new EvalResult();

        for (EvalCase tc : testCases) {
            // 1. 检索 Top-5，检查是否命中预期来源文档
            List<Document> retrieved = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .topK(5)
                    .similarityThreshold(0.7)
                    .query(tc.getQuestion())
                    .build());

            boolean hit = retrieved.stream()
                .anyMatch(doc -> tc.getExpectedSources().stream()
                    .anyMatch(expected ->
                        doc.getMetadata().getOrDefault("file_name", "").toString()
                           .contains(expected)));

            // 2. 调用 QA 服务生成回答
            String answer = qaService.ask(tc.getQuestion());

            // 3. 计算关键词覆盖率
            long matched = tc.getExpectedKeywords().stream()
                .filter(answer::contains).count();
            double rate = tc.getExpectedKeywords().isEmpty()
                ? 0.0
                : 1.0 * matched / tc.getExpectedKeywords().size();

            EvalItem item = new EvalItem();
            item.setQuestion(tc.getQuestion());
            item.setSourceHit(hit);
            item.setKeywordCoverage(rate);
            item.setAnswer(answer);
            result.getItems().add(item);
        }

        // 汇总指标
        long hitCount = result.getItems().stream().filter(EvalItem::isSourceHit).count();
        result.setTop5HitRate(testCases.isEmpty() ? 0.0 : 1.0 * hitCount / testCases.size());
        result.setAvgKeywordCoverage(
            result.getItems().stream().mapToDouble(EvalItem::getKeywordCoverage).average().orElse(0));
        return result;
    }

    /** 单个评估测试用例 */
    @Data
    public static class EvalCase {
        /** 测试问题 */
        private String question;
        /** 预期命中的来源文档名列表（如 ["order.md", "payment.md"]） */
        private List<String> expectedSources;
        /** 预期回答中应包含的关键词列表（如 ["POST", "创建", "/api/order"]） */
        private List<String> expectedKeywords;
    }

    /** 单个用例的评估明细 */
    @Data
    public static class EvalItem {
        /** 测试问题 */
        private String question;
        /** Top-5 检索是否命中预期来源 */
        private boolean sourceHit;
        /** 关键词覆盖率（0~1） */
        private double keywordCoverage;
        /** 实际检索到的来源文档列表 */
        private List<String> retrievedSources;
        /** LLM 生成的回答全文 */
        private String answer;
    }

    /** 评估结果汇总 */
    @Data
    public static class EvalResult {
        /** Top-5 命中率（0~1），检索质量的核心指标 */
        private double top5HitRate;
        /** 平均关键词覆盖率（0~1），生成质量的核心指标 */
        private double avgKeywordCoverage;
        /** 每个用例的评估明细 */
        private List<EvalItem> items = new ArrayList<>();
    }
}
