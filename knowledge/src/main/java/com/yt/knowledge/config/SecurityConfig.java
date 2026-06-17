package com.yt.knowledge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置。
 * <p>
 * 实现双模认证 + 角色权限控制：
 * </p>
 *
 * <h3>认证方式</h3>
 * <ol>
 *   <li><b>API Key</b>：通过 {@code X-API-Key} 请求头传递（由 {@link ApiKeyFilter} 处理）</li>
 *   <li><b>HTTP Basic Auth</b>：标准 Basic 认证</li>
 * </ol>
 * <p>
 * API Key 过滤器在限流过滤器之后、Basic Auth 之前执行，任一种认证通过即可。
 * </p>
 *
 * <h3>角色权限</h3>
 * <table>
 *   <tr><th>角色</th><th>用户</th><th>权限</th></tr>
 *   <tr><td>ADMIN</td><td>admin</td><td>导入、管理、问答、评估、Actuator</td></tr>
 *   <tr><td>USER</td><td>viewer</td><td>仅问答</td></tr>
 * </table>
 *
 * <h3>安全策略</h3>
 * <ul>
 *   <li>CSRF 已禁用（纯 API 服务，无 Cookie 会话）</li>
 *   <li>无状态会话（STATELESS）</li>
 *   <li>密码使用 BCrypt 加密</li>
 *   <li>用户存储在内存中（可通过配置覆盖为数据库）</li>
 *   <li>集成限流保护，防止 API 滥用</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.security.api-key:}")
    private String apiKey;

    @Value("${app.security.admin.username:admin}")
    private String adminUsername;

    @Value("${app.security.admin.password:}")
    private String adminPassword;

    @Value("${app.security.viewer.username:viewer}")
    private String viewerUsername;

    @Value("${app.security.viewer.password:viewer123}")
    private String viewerPassword;

    /**
     * 配置安全过滤链。
     * <p>
     * 关键配置：
     * <ul>
     *   <li>禁用 CSRF（API 服务无浏览器 Cookie）</li>
     *   <li>无状态会话（每次请求独立认证）</li>
     *   <li>过滤器执行顺序：限流 → API Key → Basic Auth</li>
     *   <li>按 URL 路径分配角色权限</li>
     * </ul>
     * </p>
     *
     * <h3>过滤器链顺序说明</h3>
     * <pre>
     * 请求进入 → RateLimitFilter（限流）→ ApiKeyFilter（API Key 认证）→
     * BasicAuthenticationFilter（Basic Auth）→ 业务逻辑
     * </pre>
     *
     * @param http HttpSecurity 对象
     * @param rateLimitFilter 限流过滤器（自动注入）
     * @return SecurityFilterChain 安全过滤链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter) throws Exception {
        http
            // 禁用 CSRF（API 服务不需要）
            .csrf(csrf -> csrf.disable())
            // 无状态会话（每次请求独立认证）
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 未认证时返回 401 而非重定向到登录页
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            // 授权规则配置
            .authorizeHttpRequests(auth -> auth
                // 健康检查（公开，必须在 /actuator/** 之前，否则被截胡）
                .requestMatchers("/actuator/health").permitAll()
                // Actuator 监控端点（ADMIN 专用）
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // 知识导入端点（ADMIN 专用）
                .requestMatchers("/api/knowledge/import", "/api/knowledge/import-file").hasRole("ADMIN")
                // 管理接口（ADMIN 专用）
                .requestMatchers("/api/knowledge/admin/**").hasRole("ADMIN")
                // 问答端点（登录即可，USER 和 ADMIN 都可访问）
                .requestMatchers("/api/knowledge/ask", "/api/knowledge/ask-stream").authenticated()
                // 其他所有请求都需要认证
                .anyRequest().authenticated())
            // 限流过滤器：在最外层，防止恶意请求消耗认证资源
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            // API Key 过滤器：在限流过滤器之后、Basic Auth 之前执行
            .addFilterAfter(apiKeyFilter(), RateLimitFilter.class)
            // 启用 HTTP Basic 认证
            .httpBasic(org.springframework.security.config.Customizer.withDefaults());

        return http.build();
    }

    /**
     * 创建 API Key 过滤器 Bean。
     * <p>
     * 从请求头 {@code X-API-Key} 中提取 API Key 进行认证。
     * API Key 认证成功后自动获得 ADMIN 角色。
     * </p>
     *
     * @return ApiKeyFilter 实例
     */
    @Bean
    public ApiKeyFilter apiKeyFilter() {
        return new ApiKeyFilter(apiKey);
    }

    /**
     * 内存用户存储（开发/测试用）。
     * <p>
     * 创建两个默认用户：
     * <ul>
     *   <li>admin：管理员角色，拥有全部权限</li>
     *   <li>viewer：普通用户角色，仅可问答</li>
     * </ul>
     * 生产环境应替换为数据库或 LDAP 用户存储。
     * </p>
     *
     * @return 包含 admin 和 viewer 两个用户的内存用户管理器
     */
    @Bean
    public UserDetailsService userDetailsService() {
        // 验证管理员密码是否已配置
        if (adminPassword == null || adminPassword.isEmpty()) {
            throw new IllegalStateException(
                    "请配置 app.security.admin.password");
        }

        // 管理员用户
        var admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder().encode(adminPassword))
                .roles("ADMIN")
                .build();

        // 普通用户（只读权限）
        var viewer = User.builder()
                .username(viewerUsername)
                .password(passwordEncoder().encode(viewerPassword))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, viewer);
    }
    /**
     * 密码编码器（BCrypt）。
     * <p>
     * BCrypt 是自适应哈希函数，带有盐值，能有效抵御彩虹表攻击。
     * 工作因子默认为 10，可根据服务器性能调整。
     * </p>
     *
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
