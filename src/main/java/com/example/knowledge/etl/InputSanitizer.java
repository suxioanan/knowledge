package com.example.knowledge.etl;

import org.springframework.stereotype.Component;

@Component
public class InputSanitizer {

    private static final int MAX_QUESTION_LENGTH = 2000;

    /**
     * 清洗用户输入
     */
    public String sanitize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (input.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("问题过长，最多 " + MAX_QUESTION_LENGTH + " 字符");
        }
        return input
            .replaceAll("(?i)(ignore|forget|disregard)\\s+(all|the|your|above|previous)\\s+(instructions|rules|guidelines|prompt)", "[filtered]")
            .replaceAll("(?i)system\\s*:\\s*", "")
            .replaceAll("(?i)<\\|im_start\\|>|<\\|im_end\\|>", "")
            .trim();
    }
}
