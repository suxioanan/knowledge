package com.yt.knowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class ResilienceConfig {

    @Bean
    public RestClient.Builder ollamaRestClientBuilder() {
        return RestClient.builder()
            .requestInterceptor((request, body, execution) -> {
                int maxRetries = 3;
                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    try {
                        return execution.execute(request, body);
                    } catch (Exception e) {
                        if (attempt == maxRetries) throw e;
                        long backoff = (long) Math.pow(2, attempt) * 1000;
                        log.warn("Ollama 请求失败 (第{}次)，{}ms后重试...", attempt, backoff);
                        try {
                            Thread.sleep(backoff);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw e;
                        }
                    }
                }
                throw new IllegalStateException("无法连接到 Ollama");
            });
    }
}
