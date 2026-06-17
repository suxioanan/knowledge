package com.yt.knowledge.agent;

import com.yt.agent.tools.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 样例：直接搜索知识库向量（同 JVM 调用，不走 HTTP）。
 *
 * 扩展方式：实现 AgentTool 接口 + {@code @Tool} 注解方法，声明为 Spring Bean 即可。
 * AgentAutoConfiguration 会自动收集并通过 ChatClient 注册给 LLM。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentSearchTool implements AgentTool {

    private final VectorStore vectorStore;

    /**
     * 按关键词搜索知识库中的原始 Chunk（绕过 RAG Prompt，返回原文片段）
     */
    @Tool(name = "findDocuments", description = "直接在知识库中搜索包含指定内容的文档片段，返回匹配的原文")
    public String findDocuments(
            @ToolParam(description = "要搜索的关键词或短语") String keyword,
            @ToolParam(description = "返回结果数量，默认 5") int topK) {
        log.info("Agent 调用工具: findDocuments('{}', topK={})", keyword, topK);

        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(keyword)
                .topK(Math.max(1, Math.min(topK, 10)))
                .similarityThreshold(0.5)
                .build());

        if (results.isEmpty()) {
            return "未找到与 '" + keyword + "' 相关的文档";
        }

        return results.stream()
            .map(doc -> "【来源：" + doc.getMetadata().getOrDefault("file_name", "未知")
                    + "】" + doc.getText().substring(0, Math.min(200, doc.getText().length())))
            .collect(Collectors.joining("\n\n---\n\n"));
    }
}
