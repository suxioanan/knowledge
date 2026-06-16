package com.yt.agent.config;

import com.yt.agent.tools.AgentTool;
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
import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "app.agent", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAutoConfiguration {

    @Value("${app.knowledge.base-url:http://localhost:${server.port}}")
    private String knowledgeBaseUrl;

    @Value("${app.knowledge.api-key:}")
    private String knowledgeApiKey;

    /**
     * 调用知识库 API 的 RestClient
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
     * KnowledgeTools Bean（Agent 库内置工具）
     */
    @Bean
    public KnowledgeTools knowledgeTools(RestClient knowledgeRestClient) {
        return new KnowledgeTools(knowledgeRestClient);
    }

    /**
     * Agent ChatClient：自动收集所有 AgentTool 实现，统一注册到 LLM。
     * 用户只需让自己的工具类实现 AgentTool 接口并声明为 Bean，无需修改此处代码。
     */
    @Bean("agentChatClient")
    public ChatClient agentChatClient(ChatClient.Builder builder, List<AgentTool> allTools) {
        return builder
            .defaultTools(allTools.toArray())
            .build();
    }
}
