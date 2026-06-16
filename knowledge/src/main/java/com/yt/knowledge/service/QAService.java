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

/**
 * 知识库问答服务。
 * <p>
 * 提供三种问答模式：
 * </p>
 * <ol>
 *   <li><b>标准 RAG</b>：由 {@code QuestionAnswerAdvisor} 自动完成检索 + 生成，一行代码即可</li>
 *   <li><b>增强 RAG</b>：手动检索 + {@link ContextExpander} 扩展相邻 Chunk + 自拼 Prompt 生成</li>
 *   <li><b>流式响应</b>：通过 SSE（Server-Sent Events）流式输出回答</li>
 * </ol>
 *
 * <p>
 * 所有问答入口都通过 {@link InputSanitizer} 进行安全清洗。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class QAService {

    private final ChatClient chatClient;
    private final InputSanitizer sanitizer;
    private final VectorStore vectorStore;
    private final ContextExpander contextExpander;

    /** 向量检索返回的最大结果数（默认 5） */
    @Value("${app.retrieval.top-k:5}")
    private int topK;

    /** 向量检索的相似度阈值（默认 0.7，范围 0~1） */
    @Value("${app.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;

    /** 上下文扩展时前后各取的相邻 Chunk 数量（默认 1） */
    @Value("${app.retrieval.neighbor-chunks:1}")
    private int neighborChunks;

    /**
     * 标准 RAG 问答（阻塞式）。
     * <p>
     * 由 {@code QuestionAnswerAdvisor} 自动执行检索 → 增强 Prompt → 生成回答。
     * 这是最简单的调用方式，适用于大多数场景。
     * </p>
     *
     * @param question 用户原始问题
     * @return LLM 生成的回答文本
     */
    public String ask(String question) {
        String cleaned = sanitizer.sanitize(question);
        return chatClient.prompt().user(cleaned).call().content();
    }

    /**
     * 标准 RAG 问答（流式 SSE）。
     * <p>
     * 与 {@link #ask} 相同逻辑，但通过 Flux 流式逐 Token 返回，提升用户体验。
     * </p>
     *
     * @param question 用户原始问题
     * @return 流式回答的 Flux，调用方需订阅消费
     */
    public Flux<String> askStream(String question) {
        String cleaned = sanitizer.sanitize(question);
        return chatClient.prompt().user(cleaned).stream().content();
    }

    /**
     * 增强 RAG 问答（阻塞式）。
     * <p>
     * 相比标准 ask()，多了手动检索 + 相邻 Chunk 上下文扩展环节。
     * 适用于需要更完整上下文的复杂问题（如跨段落的流程说明）。
     * </p>
     *
     * <h3>处理流程</h3>
     * <ol>
     *   <li>清洗用户问题</li>
     *   <li>向量检索 Top-K 相关 Chunk</li>
     *   <li>通过 {@link ContextExpander} 扩展相邻 Chunk</li>
     *   <li>拼接上下文（标明来源文件）</li>
     *   <li>自组 Prompt 调用 LLM 生成回答</li>
     * </ol>
     *
     * @param question 用户原始问题
     * @return LLM 生成的回答文本
     */
    public String askWithNeighborContext(String question) {
        String cleaned = sanitizer.sanitize(question);

        // 1. 检索 TopK
        List<Document> retrieved = vectorStore.similaritySearch(
            SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .query(cleaned)
                .build());

        // 2. 扩展相邻 Chunk（前后各 neighborChunks 个）
        List<Document> expanded = contextExpander.expandWithNeighbors(
            retrieved, vectorStore, neighborChunks);

        // 3. 拼接上下文，每个 Chunk 注明来源文件
        String context = expanded.stream()
            .map(doc -> "【来源：" + doc.getMetadata().getOrDefault("file_name", "未知") + "】\n"
                    + doc.getText())
            .collect(Collectors.joining("\n\n---\n\n"));

        // 4. 自组 Prompt 调用 LLM 生成回答
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
