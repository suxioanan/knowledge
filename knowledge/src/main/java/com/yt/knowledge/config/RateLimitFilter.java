package com.yt.knowledge.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // 使用 Caffeine Cache 替代 ConcurrentHashMap，自动清理过期 Bucket
    private final Cache<String, Bucket> buckets;

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${app.rate-limit.admin-requests-per-minute:300}")
    private int adminRequestsPerMinute;

    private final List<String> ipWhitelist;

    @Value("${app.security.api-key:}")
    private String configuredApiKey;

    public RateLimitFilter(@Value("${app.rate-limit.ip-whitelist:}") String ipWhitelistStr) {
        if (ipWhitelistStr == null || ipWhitelistStr.trim().isEmpty()) {
            this.ipWhitelist = Collections.emptyList();
        } else {
            this.ipWhitelist = Arrays.stream(ipWhitelistStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }

        // 创建带过期策略的 Cache：10分钟未访问则自动清理
        this.buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10000)  // 最多存储10000个IP
            .build();

        log.info("限流IP白名单: {}", this.ipWhitelist);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        if (ipWhitelist.contains(clientIp)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isAdmin = isAdminRequest(request);
        int limit = isAdmin ? adminRequestsPerMinute : requestsPerMinute;

        Bucket bucket = buckets.get(clientIp, k -> createBucket(limit));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("限流触发: IP={}, 角色={}", clientIp, isAdmin ? "ADMIN" : "USER");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                "{\"error\":\"Rate limit exceeded\",\"limit\":%d,\"unit\":\"minute\"}", limit));
        }
    }

    private Bucket createBucket(int limit) {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofMinutes(1))))
            .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        // 仅当 API Key 已配置且值匹配时，才视为 ADMIN 请求
        String apiKey = request.getHeader("X-API-Key");
        if (configuredApiKey != null && !configuredApiKey.isEmpty()
                && apiKey != null && configuredApiKey.equals(apiKey)) {
            return true;
        }
        return false;
    }
}
