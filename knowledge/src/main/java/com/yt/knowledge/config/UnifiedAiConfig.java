package com.yt.knowledge.config;

import com.yt.agent.tools.AgentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 全能 ChatClient：RAG 检索 + Agent 工具调用，LLM 自主决策使用哪个。
 * 仅在 app.agent.enabled=true 时生效。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.agent", name = "enabled", havingValue = "true")
public class UnifiedAiConfig {

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
