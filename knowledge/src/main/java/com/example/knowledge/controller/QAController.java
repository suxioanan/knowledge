package com.example.knowledge.controller;

import com.example.knowledge.service.MultiTurnQAService;
import com.example.knowledge.service.QAService;
import com.example.knowledge.service.RAGEvaluator;
import com.example.knowledge.service.RAGEvaluator.EvalResult;
import com.example.knowledge.service.RAGEvaluator.EvalCase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class QAController {

    private final QAService qaService;
    private final MultiTurnQAService multiTurnQAService;
    private final RAGEvaluator evaluator;

    // ==================== 单轮问答 ====================

    /**
     * 流式问答 — SSE（text/event-stream）
     */
    @PostMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askStream(@RequestBody QuestionRequest request) {
        return qaService.askStream(request.getQuestion())
            .map(chunk -> ServerSentEvent.<String>builder()
                .data(chunk)
                .build())
            .concatWithValues(
                ServerSentEvent.<String>builder()
                    .event("done")
                    .data("[DONE]")
                    .build());
    }

    /**
     * 阻塞式问答
     */
    @PostMapping("/ask")
    public ResponseEntity<AnswerResponse> ask(@RequestBody QuestionRequest request) {
        String answer = qaService.ask(request.getQuestion());
        return ResponseEntity.ok(new AnswerResponse(answer));
    }

    // ==================== 多轮对话 ====================

    /**
     * 多轮对话 — 阻塞式
     * Body: {"question": "...", "conversationId": "session-001"}
     */
    @PostMapping("/ask-conversation")
    public ResponseEntity<AnswerResponse> askConversation(@RequestBody ConversationRequest request) {
        String answer = multiTurnQAService.ask(
            request.getConversationId(), request.getQuestion());
        return ResponseEntity.ok(new AnswerResponse(answer));
    }

    /**
     * 多轮对话 — 流式
     */
    @PostMapping(value = "/ask-conversation-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askConversationStream(
            @RequestBody ConversationRequest request) {
        return multiTurnQAService.askStream(request.getConversationId(), request.getQuestion())
            .map(chunk -> ServerSentEvent.<String>builder()
                .data(chunk)
                .build())
            .concatWithValues(
                ServerSentEvent.<String>builder()
                    .event("done")
                    .data("[DONE]")
                    .build());
    }

    // ==================== 评估 ====================

    /**
     * 检索质量评估（仅 ADMIN）
     * Body: [{"question":"...", "expectedSources":["test.md"], "expectedKeywords":["xxx"]}]
     */
    @PostMapping("/eval")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public EvalResult evaluate(@RequestBody List<EvalCase> testCases) {
        return evaluator.evaluate(testCases);
    }

    // ==================== DTOs ====================

    @Data
    static class QuestionRequest {
        private String question;
    }

    @Data
    static class ConversationRequest {
        private String question;
        private String conversationId;
    }

    @Data
    @AllArgsConstructor
    static class AnswerResponse {
        private String answer;
    }
}
