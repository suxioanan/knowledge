package com.yt.knowledge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 安全配置相关单元测试。
 * <p>
 * 与 {@link ApiKeyFilter} 同包，以访问其 protected {@code doFilterInternal} 方法。
 * </p>
 */
@DisplayName("安全配置单元测试")
class SecurityConfigTest {

    @Nested
    @DisplayName("ApiKeyFilter")
    class ApiKeyFilterTest {

        private static final String VALID_API_KEY = "test-key-123";

        @BeforeEach
        void clearContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("有效 API Key → 认证通过，SecurityContext 设置 ADMIN+USER 角色")
        void shouldAuthenticateWithValidApiKey() throws ServletException, IOException {
            ApiKeyFilter filter = new ApiKeyFilter(VALID_API_KEY);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-API-Key", VALID_API_KEY);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertEquals("api-key-user", auth.getPrincipal());
            assertTrue(auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("无效 API Key → 不设认证但继续过滤链（由后续 Basic Auth 处理）")
        void shouldContinueChainForInvalidApiKey() throws ServletException, IOException {
            ApiKeyFilter filter = new ApiKeyFilter(VALID_API_KEY);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-API-Key", "wrong-key");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(200, response.getStatus());
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("无 API Key 头 → 过滤链继续，不设认证")
        void shouldContinueWhenNoApiKeyHeader() throws ServletException, IOException {
            ApiKeyFilter filter = new ApiKeyFilter(VALID_API_KEY);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("API Key 为空配置 → 直接放行")
        void shouldSkipWhenApiKeyNotConfigured() throws ServletException, IOException {
            ApiKeyFilter filter = new ApiKeyFilter("");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-API-Key", "anything");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(chain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("SecurityConfig Bean")
    class SecurityConfigBeanTest {

        @Test
        @DisplayName("passwordEncoder 生成 BCrypt 密码")
        void shouldUseBCryptPasswordEncoder() {
            SecurityConfig config = createConfigWithDefaults();
            var encoder = config.passwordEncoder();
            String encoded = encoder.encode("test123");
            assertTrue(encoded.startsWith("$2a$") || encoded.startsWith("$2b$"));
        }

        @Test
        @DisplayName("userDetailsService 包含 admin 和 viewer")
        void shouldCreateBothUsers() {
            SecurityConfig config = createConfigWithDefaults();
            var userDetailsService = config.userDetailsService();

            var admin = userDetailsService.loadUserByUsername("admin");
            assertNotNull(admin);
            assertTrue(admin.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

            var viewer = userDetailsService.loadUserByUsername("viewer");
            assertNotNull(viewer);
            assertTrue(viewer.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        }

        @Test
        @DisplayName("不存在用户名 → UsernameNotFoundException")
        void shouldThrowForUnknownUser() {
            SecurityConfig config = createConfigWithDefaults();
            var userDetailsService = config.userDetailsService();

            assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown"));
        }

        @Test
        @DisplayName("adminPassword 未配置 → IllegalStateException")
        void shouldThrowWhenAdminPasswordNotConfigured() {
            SecurityConfig config = new SecurityConfig();
            ReflectionTestUtils.setField(config, "adminPassword", "");
            assertThrows(IllegalStateException.class, config::userDetailsService);
        }

        /** 用默认值创建 SecurityConfig 实例 */
        private SecurityConfig createConfigWithDefaults() {
            SecurityConfig config = new SecurityConfig();
            ReflectionTestUtils.setField(config, "apiKey", "test-key");
            ReflectionTestUtils.setField(config, "adminUsername", "admin");
            ReflectionTestUtils.setField(config, "adminPassword", "admin123");
            ReflectionTestUtils.setField(config, "viewerUsername", "viewer");
            ReflectionTestUtils.setField(config, "viewerPassword", "viewer123");
            return config;
        }
    }

    @Nested
    @DisplayName("securityFilterChain")
    class SecurityFilterChainTest {

        /**
         * 使用 Spring Security 的 HttpSecurity 构建器测试安全过滤链配置。
         * 注意：此测试通过构造实际 HttpSecurity 并调用 securityFilterChain
         * 来验证配置不会抛异常，属于基本冒烟测试。
         */
        @Test
        @DisplayName("securityFilterChain 构建成功不抛异常")
        void shouldBuildFilterChainWithoutException() throws Exception {
            SecurityConfig config = createConfigWithDefaults();
            RateLimitFilter rateLimitFilter = mock(RateLimitFilter.class);

            // HttpSecurity 构造器需要 AuthenticationManagerBuilder 而非 AuthenticationManager
            HttpSecurity http = new HttpSecurity(
                mock(ObjectPostProcessor.class),
                mock(AuthenticationManagerBuilder.class),
                java.util.Map.of()
            );

            SecurityFilterChain chain = config.securityFilterChain(http, rateLimitFilter);
            assertNotNull(chain);
            assertTrue(chain.getFilters().size() > 0,
                "过滤链应包含至少一个过滤器");
        }

        /** 用默认值创建 SecurityConfig 实例 */
        private SecurityConfig createConfigWithDefaults() {
            SecurityConfig config = new SecurityConfig();
            ReflectionTestUtils.setField(config, "apiKey", "test-key");
            ReflectionTestUtils.setField(config, "adminUsername", "admin");
            ReflectionTestUtils.setField(config, "adminPassword", "admin123");
            ReflectionTestUtils.setField(config, "viewerUsername", "viewer");
            ReflectionTestUtils.setField(config, "viewerPassword", "viewer123");
            return config;
        }
    }
}
