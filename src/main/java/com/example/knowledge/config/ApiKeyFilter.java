package com.example.knowledge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API Key 认证过滤器
 *
 * 使用方式：curl -H "X-API-Key: sk-xxx" http://localhost:8080/api/knowledge/ask
 *
 * 规则：
 * - 如果配置了 apiKey 且请求 header 匹配 → 授予 ADMIN 角色
 * - 如果没有配置 apiKey（空字符串）→ 过滤器不生效，走 Basic Auth
 * - API Key 不匹配 → 继续走下一个过滤器（Basic Auth）
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private final String configuredApiKey;

    public ApiKeyFilter(String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        if (!StringUtils.hasText(configuredApiKey)) {
            chain.doFilter(request, response);
            return;
        }

        String requestApiKey = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(requestApiKey) && configuredApiKey.equals(requestApiKey)) {
            var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER"));
            var auth = new UsernamePasswordAuthenticationToken(
                "api-key-user", null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }
}
