package com.yt.agent.config;

import com.yt.agent.tools.KnowledgeTools;
import com.yt.agent.AgentProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@AutoConfiguration
@ConditionalOnProperty(prefix = "app.agent", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAutoConfiguration {

    @Value("${app.knowledge.base-url:http://localhost:${server.port}}")
    private String knowledgeBaseUrl;

    @Value("${app.knowledge.api-key:}")
    private String knowledgeApiKey;

    /**
     * 调用知识库 API 的 RestClient（最先创建，后续 Bean 依赖它）
     */
    @Bean
    public RestClient knowledgeRestClient(RestClient.Builder builder) {
        var b = builder.baseUrl(knowledgeBaseUrl);
        if (knowledgeApiKey != null && !knowledgeApiKey.isBlank()) {
            b.defaultHeader("X-API-Key", knowledgeApiKey);
        } else {
            String basicAuth = Base64.getEncoder()
                .encodeToString("admin:admin".getBytes());
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth);
        }
        return b.build();
    }

    /**
     * KnowledgeTools Bean（直接创建，不依赖 @ComponentScan）
     */
    @Bean
    public KnowledgeTools knowledgeTools(RestClient knowledgeRestClient) {
        return new KnowledgeTools(knowledgeRestClient);
    }

    /**
     * Agent 专用 ChatClient：基于 ChatClient.Builder，追加 tools。
     * RagConfig 中的 ChatClient 继续用于纯 RAG 问答，此处创建带工具调用的 bean。
     */
    @Bean("agentChatClient")
    public ChatClient agentChatClient(ChatClient.Builder builder, KnowledgeTools tools) {
        return builder
            .defaultTools(tools)
            .build();
    }
}
