package com.yt.knowledge.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RAGEvaluator {

    private final VectorStore vectorStore;
    private final QAService qaService;

    public EvalResult evaluate(List<EvalCase> testCases) {
        EvalResult result = new EvalResult();

        for (EvalCase tc : testCases) {
            List<Document> retrieved = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .topK(5)
                    .similarityThreshold(0.7)
                    .build());

            boolean hit = retrieved.stream()
                .anyMatch(doc -> tc.getExpectedSources().stream()
                    .anyMatch(expected ->
                        doc.getMetadata().getOrDefault("file_name", "").toString()
                           .contains(expected)));

            String answer = qaService.ask(tc.getQuestion());

            long matched = tc.getExpectedKeywords().stream()
                .filter(answer::contains).count();
            double rate = 1.0 * matched / tc.getExpectedKeywords().size();

            EvalItem item = new EvalItem();
            item.setQuestion(tc.getQuestion());
            item.setSourceHit(hit);
            item.setKeywordCoverage(rate);
            item.setAnswer(answer);
            result.getItems().add(item);
        }

        long hitCount = result.getItems().stream().filter(EvalItem::isSourceHit).count();
        result.setTop5HitRate(1.0 * hitCount / testCases.size());
        result.setAvgKeywordCoverage(
            result.getItems().stream().mapToDouble(EvalItem::getKeywordCoverage).average().orElse(0));
        return result;
    }

    @Data
    public static class EvalCase {
        private String question;
        private List<String> expectedSources;
        private List<String> expectedKeywords;
    }

    @Data
    public static class EvalItem {
        private String question;
        private boolean sourceHit;
        private double keywordCoverage;
        private List<String> retrievedSources;
        private String answer;
    }

    @Data
    public static class EvalResult {
        private double top5HitRate;
        private double avgKeywordCoverage;
        private List<EvalItem> items = new ArrayList<>();
    }
}
