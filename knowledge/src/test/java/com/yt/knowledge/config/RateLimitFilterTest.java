package com.yt.knowledge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link RateLimitFilter} 单元测试。
 * <p>
 * 验证限流逻辑：令牌桶消费、白名单放行、超限拒绝。
 * </p>
 */
@DisplayName("RateLimitFilter 单元测试")
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter("127.0.0.1,10.0.0.1");
        // 禁用限流以便单独测试各个功能，避免令牌桶状态污染
        ReflectionTestUtils.setField(filter, "rateLimitEnabled", false);

        request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Nested
    @DisplayName("限流开关")
    class RateLimitToggle {

        @Test
        @DisplayName("限流禁用 → 直接放行")
        void shouldPassThroughWhenDisabled() throws ServletException, IOException {
            filter.doFilterInternal(request, response, chain);
            verify(chain).doFilter(request, response);
            assertEquals(200, response.getStatus());
        }

        @Test
        @DisplayName("限流启用 → 检查令牌桶")
        void shouldCheckBucketWhenEnabled() throws ServletException, IOException {
            ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
            ReflectionTestUtils.setField(filter, "requestsPerMinute", 1000);

            filter.doFilterInternal(request, response, chain);
            verify(chain).doFilter(request, response);
            assertEquals(200, response.getStatus());
        }
    }

    @Nested
    @DisplayName("IP 白名单")
    class IpWhitelist {

        @Test
        @DisplayName("白名单 IP → 直接放行，不创建 Bucket")
        void shouldBypassWhitelistedIp() throws ServletException, IOException {
            ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
            request.setRemoteAddr("127.0.0.1");

            filter.doFilterInternal(request, response, chain);
            verify(chain).doFilter(request, response);
            assertEquals(200, response.getStatus());
        }

        @Test
        @DisplayName("白名单 IP（第二个） → 直接放行")
        void shouldBypassSecondWhitelistedIp() throws ServletException, IOException {
            ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
            request.setRemoteAddr("10.0.0.1");

            filter.doFilterInternal(request, response, chain);
            verify(chain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("超限拒绝")
    class RateLimitExceeded {

        @Test
        @DisplayName("令牌耗尽 → 返回 429 Too Many Requests")
        void shouldReturn429WhenRateLimited() throws ServletException, IOException {
            ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
            ReflectionTestUtils.setField(filter, "requestsPerMinute", 0); // 0 令牌

            filter.doFilterInternal(request, response, chain);

            assertEquals(429, response.getStatus());
            assertTrue(response.getContentAsString().contains("Rate limit exceeded"));
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("客户端 IP 获取")
    class ClientIpResolution {

        @Test
        @DisplayName("X-Forwarded-For 头 → 取第一个 IP")
        void shouldUseXForwardedForHeader() throws ServletException, IOException {
            // enabled + high limit to pass rate check
            ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
            ReflectionTestUtils.setField(filter, "requestsPerMinute", 1000);
            request.addHeader("X-Forwarded-For", "10.10.0.5, 192.168.1.1");

            filter.doFilterInternal(request, response, chain);
            // token consumed for 10.10.0.5, should pass
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("X-Real-IP 头 → 使用该 IP")
        void shouldUseXRealIpHeader() throws ServletException, IOException {
            ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
            ReflectionTestUtils.setField(filter, "requestsPerMinute", 1000);
            request.addHeader("X-Real-IP", "10.20.30.40");

            filter.doFilterInternal(request, response, chain);
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("无代理头 → 使用 RemoteAddr")
        void shouldFallbackToRemoteAddr() throws ServletException, IOException {
            ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
            ReflectionTestUtils.setField(filter, "requestsPerMinute", 1000);

            filter.doFilterInternal(request, response, chain);
            verify(chain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("构造器")
    class Constructor {

        @Test
        @DisplayName("空 IP 白名单 → 正常创建")
        void shouldHandleEmptyWhitelist() {
            RateLimitFilter empty = new RateLimitFilter("");
            assertNotNull(empty);
        }

        @Test
        @DisplayName("null IP 白名单 → 正常创建")
        void shouldHandleNullWhitelist() {
            RateLimitFilter nullFilter = new RateLimitFilter(null);
            assertNotNull(nullFilter);
        }
    }
}
