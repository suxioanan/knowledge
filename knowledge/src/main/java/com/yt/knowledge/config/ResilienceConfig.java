package com.yt.knowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 韧性配置（Resilience）。
 * <p>
 * 为 Ollama HTTP 调用配置指数退避重试策略，提升服务容错能力。
 * </p>
 *
 * <h3>重试策略</h3>
 * <ul>
 *   <li><b>最大重试次数</b>：3 次</li>
 *   <li><b>退避算法</b>：指数退避（2^n 秒），即 2s → 4s → 8s</li>
 *   <li><b>中断处理</b>：正确处理 {@code InterruptedException}，恢复线程中断标志</li>
 * </ul>
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    /**
     * 创建带重试的 Ollama {@link RestClient.Builder}。
     * <p>
     * 通过 Spring AI 自动配置机制，此 Builder 会被注入到 Ollama 相关组件中，
     * 使 Embedding 和 Chat 请求都具备自动重试能力。
     * </p>
     *
     * @return 配置了指数退避重试的 RestClient.Builder
     */
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
                        // 指数退避：2^attempt 秒
                        long backoff = (long) Math.pow(2, attempt) * 1000;
                        log.warn("Ollama 请求失败 (第{}次)，{}ms后重试...", attempt, backoff);
                        try {
                            Thread.sleep(backoff);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();  // 恢复中断标志
                            throw e;
                        }
                    }
                }
                // 理论上不会到达这里（最后一次失败会直接 throw）
                throw new IllegalStateException("无法连接到 Ollama");
            });
    }
}
