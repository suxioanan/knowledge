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
 * API Key 过滤器在 Basic Auth 之前执行，任一种认证通过即可。
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
     *   <li>API Key 过滤器插在 Basic Auth 之前</li>
     *   <li>按 URL 路径分配角色权限</li>
     * </ul>
     * </p>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                // Actuator 监控端点（ADMIN 专用）
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // 知识导入端点（ADMIN 专用）
                .requestMatchers("/api/knowledge/import", "/api/knowledge/import-file").hasRole("ADMIN")
                // 管理接口（ADMIN 专用）
                .requestMatchers("/api/knowledge/admin/**").hasRole("ADMIN")
                // 问答端点（登录即可）
                .requestMatchers("/api/knowledge/ask", "/api/knowledge/ask-stream").authenticated()
                // 健康检查（公开）
                .requestMatchers("/actuator/health").permitAll()
                // 其他请求（需要认证）
                .anyRequest().authenticated())
            // API Key 过滤器插入在 UsernamePasswordAuthenticationFilter 之前
            .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
            .httpBasic(org.springframework.security.config.Customizer.withDefaults());

        return http.build();
    }

    /**
     * 创建 API Key 过滤器 Bean。
     */
    @Bean
    public ApiKeyFilter apiKeyFilter() {
        return new ApiKeyFilter(apiKey);
    }

    /**
     * 内存用户存储（开发/测试用）。
     * <p>
     * 生产环境应替换为数据库或 LDAP 用户存储。
     * </p>
     *
     * @return 包含 admin 和 viewer 两个用户的内存用户管理器
     */
    @Bean
    public UserDetailsService userDetailsService() {
        var admin = User.builder()
            .username(adminUsername)
            .password(passwordEncoder().encode(
                adminPassword != null ? adminPassword : "admin"))
            .roles("ADMIN")
            .build();

        var viewer = User.builder()
            .username(viewerUsername)
            .password(passwordEncoder().encode(viewerPassword))
            .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(admin, viewer);
    }

    /**
     * 密码编码器（BCrypt）。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
