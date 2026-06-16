package com.yt.agent.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具集：提供给 LLM 调用的能力。
 * Spring AI 1.0 GA 通过 @Tool 注解自动发现和注册。
 */
@Slf4j
@RequiredArgsConstructor
public class KnowledgeTools {

    private final RestClient knowledgeRestClient;

    /**
     * 搜索知识库中的文档内容
     */
    @Tool(name = "searchKnowledge", description = "搜索知识库中的文档内容，根据用户问题返回相关答案")
    public String searchKnowledge(
            @ToolParam(description = "要搜索的问题") String question) {
        log.info("Agent 调用工具: searchKnowledge('{}')", question);
        try {
            var response = knowledgeRestClient.post()
                .uri("/api/knowledge/ask")
                .header("Content-Type", "application/json")
                .body(Map.of("question", question))
                .retrieve()
                .body(AskResponse.class);

            if (response != null && response.answer() != null) {
                return response.answer();
            }
            return "知识库未返回相关信息";
        } catch (Exception e) {
            log.error("知识库查询失败", e);
            return "知识库服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 获取知识库统计信息
     */
    @Tool(name = "getKnowledgeStats", description = "获取知识库的统计信息，包括已索引的文档数量、分类等")
    public String getKnowledgeStats() {
        log.info("Agent 调用工具: getKnowledgeStats()");
        try {
            var response = knowledgeRestClient.get()
                .uri("/api/knowledge/admin/stats")
                .retrieve()
                .body(Map.class);
            return response != null ? response.toString() : "暂无统计信息";
        } catch (Exception e) {
            log.error("获取知识库统计失败", e);
            return "知识库服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 获取当前日期和时间
     */
    @Tool(name = "getCurrentDateTime", description = "获取当前的日期和时间")
    public String getCurrentDateTime() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
        log.info("Agent 调用工具: getCurrentDateTime() → {}", now);
        return "当前时间：" + now;
    }

    /**
     * 查询知识库支持的所有文档分类
     */
    @Tool(name = "listCategories", description = "列出知识库中所有文档分类（api/database/product/wiki/other）")
    public String listCategories() {
        log.info("Agent 调用工具: listCategories()");
        List<String> categories = Arrays.asList("api", "database", "product", "wiki", "other");
        return "知识库支持的文档分类：" + String.join("、", categories)
            + "。可以通过分类关键词辅助 searchKnowledge 工具检索。";
    }

    // ---- 内部 DTO ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AskResponse(String answer) {}
}
