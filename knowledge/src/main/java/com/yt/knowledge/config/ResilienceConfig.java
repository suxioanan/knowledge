package com.yt.knowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
            .requestInterceptor(new RetryInterceptor());
    }

    /**
     * 独立的重试拦截器类，支持更灵活的重试策略
     */
    static class RetryInterceptor implements ClientHttpRequestInterceptor {

        private static final int MAX_RETRIES = 3;
        private static final long INITIAL_BACKOFF_MS = 2000; // 2秒

        @Override
        public ClientHttpResponse intercept(org.springframework.http.HttpRequest request,
                                           byte[] body,
                                           ClientHttpRequestExecution execution) throws IOException {
            Exception lastException = null;

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    return execution.execute(request, body);
                } catch (Exception e) {
                    lastException = e;

                    if (attempt == MAX_RETRIES) {
                        log.error("Ollama 请求最终失败: {} {}", request.getMethod(), request.getURI());
                        break;
                    }

                    // 指数退避：2s → 4s → 8s
                    long backoff = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
                    log.warn("Ollama 请求失败 (第{}/{}次)，{}ms后重试... 错误: {}",
                            attempt, MAX_RETRIES, backoff, e.getMessage());

                    try {
                        TimeUnit.MILLISECONDS.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("重试被中断", ie);
                    }
                }
            }

            throw new IOException("Ollama 请求失败，已重试" + MAX_RETRIES + "次", lastException);
        }
    }
}
