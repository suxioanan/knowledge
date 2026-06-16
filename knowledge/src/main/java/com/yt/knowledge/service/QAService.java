package com.yt.knowledge.service;

import com.yt.knowledge.etl.ContextExpander;
import com.yt.knowledge.etl.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QAService {

    private final ChatClient chatClient;
    private final InputSanitizer sanitizer;
    private final VectorStore vectorStore;
    private final ContextExpander contextExpander;

    @Value("${app.retrieval.top-k:5}")
    private int topK;

    @Value("${app.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${app.retrieval.neighbor-chunks:1}")
    private int neighborChunks;

    /**
     * 标准问答 — 由 QuestionAnswerAdvisor 自动检索 + 生成
     */
    public String ask(String question) {
        String cleaned = sanitizer.sanitize(question);
        return chatClient.prompt().user(cleaned).call().content();
    }

    /**
     * 标准问答 — 流式
     */
    public Flux<String> askStream(String question) {
        String cleaned = sanitizer.sanitize(question);
        return chatClient.prompt().user(cleaned).stream().content();
    }

    /**
     * 增强问答 — 手动检索 + ContextExpander 扩展相邻 Chunk + 生成。
     * 比标准 ask() 多了"父子 Chunk 上下文扩展"环节，回答更完整。
     */
    public String askWithNeighborContext(String question) {
        String cleaned = sanitizer.sanitize(question);

        // 1. 检索 TopK
        List<Document> retrieved = vectorStore.similaritySearch(
            SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());

        // 2. 扩展相邻 Chunk（前后各 neighborChunks 个）
        List<Document> expanded = contextExpander.expandWithNeighbors(
            retrieved, vectorStore, neighborChunks);

        // 3. 拼接上下文
        String context = expanded.stream()
            .map(doc -> "【来源：" + doc.getMetadata().getOrDefault("file_name", "未知") + "】\n"
                    + doc.getText())
            .collect(Collectors.joining("\n\n---\n\n"));

        // 4. 生成回答
        String prompt = """
            根据以下参考内容回答问题。如果参考内容不足以回答，请说明需要哪些额外信息。

            ## 参考内容
            %s

            ## 用户问题
            %s

            ## 回答
            """.formatted(context, cleaned);

        return chatClient.prompt().user(prompt).call().content();
    }
}
