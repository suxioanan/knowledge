package com.example.knowledge.controller;

import com.example.knowledge.service.QAService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class QAController {

    private final QAService qaService;

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

    @Data
    static class QuestionRequest {
        private String question;
    }

    @Data
    @AllArgsConstructor
    static class AnswerResponse {
        private String answer;
    }
}
