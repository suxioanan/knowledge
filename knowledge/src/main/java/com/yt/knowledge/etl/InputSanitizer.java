package com.yt.knowledge.etl;

import org.springframework.stereotype.Component;

/**
 * 用户输入安全清洗器。
 * <p>
 * 对用户问题执行安全校验，防止 Prompt Injection 攻击：
 * </p>
 * <ol>
 *   <li><b>长度限制</b>：超过 {@value #MAX_QUESTION_LENGTH} 字符拒绝</li>
 *   <li><b>空值校验</b>：null 或空白字符串拒绝</li>
 *   <li><b>注入模式过滤</b>：替换"ignore all instructions"等常见注入指令</li>
 *   <li><b>系统标记过滤</b>：移除 {@code system:} 前缀和 {@code <|im_start|> / <|im_end|>} 标记</li>
 * </ol>
 */
@Component
public class InputSanitizer {

    /** 用户问题最大允许字符数 */
    private static final int MAX_QUESTION_LENGTH = 2000;

    /**
     * 清洗并校验用户输入。
     *
     * @param input 原始用户问题
     * @return 清洗后的安全输入
     * @throws IllegalArgumentException 如果输入为空或超过长度限制
     */
    public String sanitize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (input.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("问题过长，最多 " + MAX_QUESTION_LENGTH + " 字符");
        }
        return input
            // 过滤常见的 Prompt Injection 指令
            .replaceAll(
                "(?i)(ignore|forget|disregard)\\s+(all|the|your|above|previous)\\s+(instructions|rules|guidelines|prompt)",
                "[filtered]")
            // 移除系统角色伪装（如 "system: 你是一只猫"）
            .replaceAll("(?i)system\\s*:\\s*", "")
            // 移除 LLM 特殊标记（ChatML 格式标记）
            .replaceAll("(?i)<\\|im_start\\|>|<\\|im_end\\|>", "")
            .trim();
    }
}
