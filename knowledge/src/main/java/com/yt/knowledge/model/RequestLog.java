package com.yt.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLog {

    private String requestId;

    private String userId;

    private String endpoint;

    private String method;

    private String requestSummary;

    private String status;

    private Long durationMs;

    private LocalDateTime timestamp;

    private String errorMessage;

    private String metadata;
}

