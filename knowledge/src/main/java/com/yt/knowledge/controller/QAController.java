package com.yt.knowledge.controller;

import com.yt.knowledge.service.MultiTurnQAService;
import com.yt.knowledge.service.QAService;
import com.yt.knowledge.service.RAGEvaluator;
import com.yt.knowledge.service.RAGEvaluator.EvalResult;
import com.yt.knowledge.service.RAGEvaluator.EvalCase;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/knowledge")
public class QAController {

    private final QAService qaService;
    private final MultiTurnQAService multiTurnQAService;
    private final RAGEvaluator evaluator;
    private final ObjectProvider<ChatClient> agentChatClientProvider;
    private final ObjectProvider<ChatClient> unifiedChatClientProvider;

    public QAController(QAService qaService,
                         MultiTurnQAService multiTurnQAService,
                         RAGEvaluator evaluator,
                         @Qualifier("agentChatClient") ObjectProvider<ChatClient> agentChatClientProvider,
                         @Qualifier("unifiedChatClient") ObjectProvider<ChatClient> unifiedChatClientProvider) {
        this.qaService = qaService;
        this.multiTurnQAService = multiTurnQAService;
        this.evaluator = evaluator;
        this.agentChatClientProvider = agentChatClientProvider;
        this.unifiedChatClientProvider = unifiedChatClientProvider;
    }

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

    // ==================== Agent 模式（条件可用） ====================

    /**
     * Agent 对话 — LLM 自主调用工具（需 app.agent.enabled=true）
     * POST /api/knowledge/agent
     * Body: {"message": "知识库里有哪些文档？现在几点？"}
     */
    @PostMapping("/agent")
    public ResponseEntity<AnswerResponse> agentChat(@RequestBody AgentChatRequest request) {
        ChatClient agentClient = agentChatClientProvider.getIfAvailable();
        if (agentClient == null) {
            return ResponseEntity.ok(new AnswerResponse(
                "Agent 功能未启用。请在 application.yml 中设置 app.agent.enabled=true"));
        }
        String answer = agentClient.prompt()
            .user(request.getMessage())
            .call()
            .content();
        return ResponseEntity.ok(new AnswerResponse(answer));
    }

    /**
     * Agent 对话 — 流式
     */
    @PostMapping(value = "/agent-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> agentChatStream(@RequestBody AgentChatRequest request) {
        ChatClient agentClient = agentChatClientProvider.getIfAvailable();
        if (agentClient == null) {
            return Flux.just(
                ServerSentEvent.<String>builder()
                    .data("Agent 功能未启用。请在 application.yml 中设置 app.agent.enabled=true")
                    .build());
        }
        return agentClient.prompt()
            .user(request.getMessage())
            .stream()
            .content()
            .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build())
            .concatWithValues(
                ServerSentEvent.<String>builder().event("done").data("[DONE]").build());
    }

    // ==================== 全能 AI（RAG + Agent 合体） ====================

    /**
     * 全能问答 — LLM 同时拥有知识库上下文 + 工具调用能力，自主决定用哪个。
     * 需 app.agent.enabled=true。
     */
    @PostMapping("/ask/ai")
    public ResponseEntity<AnswerResponse> askAi(@RequestBody QuestionRequest request) {
        ChatClient client = unifiedChatClientProvider.getIfAvailable();
        if (client == null) {
            return ResponseEntity.ok(new AnswerResponse(
                "AI 功能未启用。请在 application.yml 中设置 app.agent.enabled=true"));
        }
        String answer = client.prompt()
            .user(request.getQuestion())
            .call()
            .content();
        return ResponseEntity.ok(new AnswerResponse(answer));
    }

    /**
     * 全能问答 — 流式
     */
    @PostMapping(value = "/ask/ai-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askAiStream(@RequestBody QuestionRequest request) {
        ChatClient client = unifiedChatClientProvider.getIfAvailable();
        if (client == null) {
            return Flux.just(
                ServerSentEvent.<String>builder()
                    .data("AI 功能未启用。请在 application.yml 中设置 app.agent.enabled=true")
                    .build());
        }
        return client.prompt()
            .user(request.getQuestion())
            .stream()
            .content()
            .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build())
            .concatWithValues(
                ServerSentEvent.<String>builder().event("done").data("[DONE]").build());
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

    @Data
    static class AgentChatRequest {
        private String message;
    }
}
