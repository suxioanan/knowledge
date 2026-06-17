package com.yt.knowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yt.knowledge.model.RequestLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

@Aspect
@Component
@Slf4j
public class RequestLoggingAspect {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping)")
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        HttpServletRequest request = getHttpServletRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        RequestLog requestLog = RequestLog.builder()
            .requestId(requestId)
            .userId(getUserId(request))
            .endpoint(request != null ? request.getRequestURI() : "unknown")
            .method(signature.getName())
            .requestSummary(buildRequestSummary(joinPoint.getArgs()))
            .timestamp(LocalDateTime.now())
            .build();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            requestLog.setDurationMs(duration);
            requestLog.setStatus("SUCCESS");
            log.info("✅ [{}] {} | 耗时: {}ms | 用户: {} | 参数: {}",
                requestId,
                requestLog.getEndpoint(),
                duration,
                requestLog.getUserId(),
                requestLog.getRequestSummary());

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;

            requestLog.setDurationMs(duration);
            requestLog.setStatus("ERROR");
            requestLog.setErrorMessage(e.getMessage());
            log.error("❌ [{}] {} | 耗时: {}ms | 异常: {} | 参数: {}",
                requestId,
                requestLog.getEndpoint(),
                duration,
                e.getMessage(),
                requestLog.getRequestSummary());

            throw e;
        }
    }

    private HttpServletRequest getHttpServletRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private String getUserId(HttpServletRequest request) {
        if (request == null) {
            return "anonymous";
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isEmpty()) {
            return "api_key:" + Integer.toHexString(apiKey.hashCode());
        }

        return request.getRemoteAddr();
    }

    private String buildRequestSummary(Object[] args) {
        if (args == null || args.length == 0) {
            return "{}";
        }

        try {
            Object firstArg = args[0];
            if (firstArg != null) {
                String json = objectMapper.writeValueAsString(firstArg);
                return json.length() > 200 ? json.substring(0, 200) + "..." : json;
            }
            return "{}";
        } catch (Exception e) {
            return Arrays.toString(args);
        }
    }
}
