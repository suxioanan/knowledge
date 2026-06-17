package com.yt.knowledge.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Query 改写服务（指代消解）。
 * <p>
 * 在多轮对话场景中，用户经常使用指代词（如"它"、"这个"、"然后呢"）。
 * 本服务通过 LLM 根据对话历史将这些不完整的查询改写为独立、完整的问题，
 * 从而提高检索准确率。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * 用户: "订单创建接口有哪些必填参数？"
 * AI: "userId、items、address"
 * 用户: "然后呢？"
 * 改写后: "订单创建接口的可选参数有哪些？"
 * </pre>
 *
 * <p>
 * 注意：当前使用内存存储对话历史，重启后丢失。生产环境建议使用 Redis。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ChatClient.Builder chatClientBuilder;

    // Caffeine 缓存：30 分钟未访问自动删除，最多 1000 个会话
    private final Cache<String, StringBuilder> conversationHistory = Caffeine.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build();

    /**
     * 基于对话历史改写当前查询（指代消解）
     */
    public String rewrite(String conversationId, String currentQuery) {
        if (conversationId == null || conversationId.isEmpty()) {
            return currentQuery;
        }

        if (!needsRewrite(currentQuery)) {
            log.debug("无需改写: {}", currentQuery);
            addToHistory(conversationId, "用户", currentQuery);
            return currentQuery;
        }

        try {
            String history = getHistoryText(conversationId);

            if (history.isEmpty()) {
                return currentQuery;
            }

            String rewrittenQuery = doRewrite(history, currentQuery);
            log.info("Query改写: '{}' → '{}'", currentQuery, rewrittenQuery);

            addToHistory(conversationId, "用户", rewrittenQuery);
            return rewrittenQuery;
        } catch (Exception e) {
            log.error("Query改写失败，使用原始查询: {}", e.getMessage());
            return currentQuery;
        }
    }

    /**
     * 记录对话到历史
     */
    public void recordResponse(String conversationId, String response) {
        if (conversationId != null && !conversationId.isEmpty()) {
            addToHistory(conversationId, "助手", response);
        }
    }

    private void addToHistory(String conversationId, String role, String message) {
        conversationHistory.get(conversationId, k -> new StringBuilder())
            .append(role).append(": ").append(message).append("\n");

        // 限制历史长度（最多保留最近10轮）
        StringBuilder history = conversationHistory.getIfPresent(conversationId);
        if (history != null && history.length() > 2000) {
            // 简单截断，保留后半部分
            int startIndex = history.length() - 2000;
            int newlineIndex = history.indexOf("\n", startIndex);
            if (newlineIndex > 0) {
                history.delete(0, newlineIndex + 1);
            }
        }
    }

    private String getHistoryText(String conversationId) {
        StringBuilder history = conversationHistory.getIfPresent(conversationId);
        return history != null ? history.toString() : "";
    }

    private String doRewrite(String history, String currentQuery) {
        String prompt = """
            你是一个查询改写助手。根据对话历史，将用户的最新查询改写为独立、完整的问题。
            
            ## 规则
            1. 如果最新查询包含指代词（如"它"、"这个"、"然后呢"），根据上文补全
            2. 如果最新查询已经是完整问题，保持原样
            3. 只输出改写后的查询，不要任何解释或额外内容
            
            ## 对话历史
            %s
            
            ## 用户最新查询
            %s
            
            ## 改写后的查询
            """.formatted(history, currentQuery);

        return chatClientBuilder.build().prompt()
            .user(prompt)
            .call()
            .content()
            .trim();
    }

    private boolean needsRewrite(String query) {
        String lowerQuery = query.toLowerCase();
        String[] pronouns = {"它", "他", "她", "这个", "那个", "哪些", "然后", "还有", "吗", "呢"};

        for (String pronoun : pronouns) {
            if (lowerQuery.contains(pronoun)) {
                return true;
            }
        }

        return query.length() < 10;
    }
}
