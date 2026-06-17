package com.yt.knowledge.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.security.access.AccessDeniedException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class KnowledgeExceptionHandler {

    /**
     * 处理权限不足异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        Map<String, String> body = new HashMap<>();
        body.put("error", "ACCESS_DENIED");
        body.put("message", "您没有权限执行此操作");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * 处理非法参数异常（如路径验证失败）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        Map<String, String> body = new HashMap<>();
        body.put("error", "BAD_REQUEST");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        Map<String, String> body = new HashMap<>();
        body.put("error", "INTERNAL_ERROR");
        body.put("message", "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {
        log.error("未处理的异常", e);
        Map<String, String> body = new HashMap<>();
        body.put("error", "INTERNAL_ERROR");
        body.put("message", "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
