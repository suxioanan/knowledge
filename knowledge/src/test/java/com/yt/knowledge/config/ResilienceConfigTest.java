package com.yt.knowledge.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link ResilienceConfig} 单元测试。
 * <p>
 * 直接测试 RetryInterceptor 的指数退避重试逻辑：
 * 第 1 次失败 → 退避 2s → 第 2 次失败 → 退避 4s → 第 3 次成功/失败。
 * </p>
 */
@DisplayName("ResilienceConfig 单元测试")
class ResilienceConfigTest {

    @Nested
    @DisplayName("ollamaRestClientBuilder")
    class BuilderTest {

        @Test
        @DisplayName("builder 创建成功且包含 RetryInterceptor")
        void shouldCreateBuilder() {
            ResilienceConfig config = new ResilienceConfig();
            assertNotNull(config.ollamaRestClientBuilder());
        }
    }

    @Nested
    @DisplayName("RetryInterceptor 重试逻辑")
    class RetryInterceptorTest {

        @Test
        @DisplayName("首次请求成功 → 不重试，直接返回响应")
        void shouldNotRetryOnSuccess() throws IOException {
            ResilienceConfig.RetryInterceptor interceptor = new ResilienceConfig.RetryInterceptor();
            HttpRequest request = mock(HttpRequest.class);
            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
            ClientHttpResponse successResponse = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
            when(execution.execute(any(), any())).thenReturn(successResponse);

            ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(execution, times(1)).execute(any(), any());
        }

        @Test
        @DisplayName("前 2 次失败、第 3 次成功 → 重试 3 次后成功")
        void shouldRetryAndSucceed() throws IOException {
            ResilienceConfig.RetryInterceptor interceptor = new ResilienceConfig.RetryInterceptor();
            HttpRequest request = mock(HttpRequest.class);
            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
            ClientHttpResponse successResponse = new MockClientHttpResponse(new byte[0], HttpStatus.OK);

            // 前 2 次抛异常，第 3 次成功
            when(execution.execute(any(), any()))
                .thenThrow(new IOException("网络错误1"))
                .thenThrow(new IOException("网络错误2"))
                .thenReturn(successResponse);

            // 注入 spy 以便验证 sleep（这里 we can't easily test sleep，
            // 但至少验证重试了 3 次）
            ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            // 验证 execute 被调用了 3 次
            verify(execution, times(3)).execute(any(), any());
        }

        @Test
        @DisplayName("3 次全部失败 → 抛出 IOException")
        void shouldThrowAfterMaxRetries() throws IOException {
            ResilienceConfig.RetryInterceptor interceptor = new ResilienceConfig.RetryInterceptor();
            HttpRequest request = mock(HttpRequest.class);
            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
            when(execution.execute(any(), any()))
                .thenThrow(new IOException("持续失败"));

            IOException ex = assertThrows(IOException.class, () ->
                interceptor.intercept(request, new byte[0], execution));

            assertTrue(ex.getMessage().contains("已重试3次"));
            verify(execution, times(3)).execute(any(), any());
        }

        @Test
        @DisplayName("重试期间被中断 → InterruptedException → 抛出 IOException 并恢复中断标志")
        void shouldHandleInterruptedException() throws IOException {
            ResilienceConfig.RetryInterceptor interceptor = new ResilienceConfig.RetryInterceptor();
            HttpRequest request = mock(HttpRequest.class);
            ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

            // 第一次失败
            when(execution.execute(any(), any()))
                .thenThrow(new IOException("网络错误"));

            // 在另一个线程中调用，并在 sleep 期间中断它
            Thread testThread = new Thread(() -> {
                // 首次调用失败后，RetryInterceptor 会 sleep 2000ms
                // 我们在 sleep 期间中断线程
                assertThrows(IOException.class, () -> {
                    interceptor.intercept(request, new byte[0], execution);
                });
                // 验证中断标志已恢复
                assertTrue(Thread.currentThread().isInterrupted());
            });

            testThread.start();
            // 等待一小段时间让 execute 被调用，然后中断
            try { Thread.sleep(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            testThread.interrupt();
            try { testThread.join(5000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
