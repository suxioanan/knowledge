package com.yt.knowledge.config;

import com.yt.agent.tools.AgentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 全能 AI 配置：RAG 检索 + Agent Tool Calling 融合模式。
 * <p>
 * 创建 {@code unifiedChatClient}，同时拥有知识库检索能力和工具调用能力，
 * LLM 将自主决策是用知识库回答、调用外部工具、还是两者结合。
 * </p>
 *
 * <p>
 * 仅在 {@code app.agent.enabled=true} 时生效（条件装配）。
 * 所有实现了 {@link AgentTool} 接口的工具类会被 Spring 自动收集并注册给 LLM。
 * </p>
 *
 * <p>
 * 对应的 API 端点：{@code POST /api/knowledge/ask/ai} 和 {@code /api/knowledge/ask/ai-stream}。
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.agent", name = "enabled", havingValue = "true")
public class UnifiedAiConfig {

    /**
     * 创建全能 ChatClient（RAG + Agent）。
     * <p>
     * 相比 {@link RagConfig#chatClient}，此 Client 额外注册了所有 AgentTool，
     * 但不包含多轮对话 Advisor（可通过外部传入）。
     * </p>
     *
     * @param builder    ChatClient 构建器
     * @param qaAdvisor  RAG 检索 Advisor（注入检索上下文）
     * @param allTools   Spring 自动收集的所有 AgentTool 实现
     * @return 全能 ChatClient
     */
    @Bean("unifiedChatClient")
    public ChatClient unifiedChatClient(ChatClient.Builder builder,
                                         QuestionAnswerAdvisor qaAdvisor,
                                         List<AgentTool> allTools) {
        return builder
            .defaultAdvisors(qaAdvisor)
            .defaultTools(allTools.toArray())
            .build();
    }
}
