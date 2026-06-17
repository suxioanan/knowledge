package com.yt.knowledge.service;

import com.yt.knowledge.etl.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 多轮对话问答服务。
 * <p>
 * 通过 {@code MessageChatMemoryAdvisor} 保持会话上下文，
 * 不同会话通过 {@code conversationId} 区分，自动维护各自的历史消息（最多 10 条）。
 * </p>
 *
 * <p>
 * 使用方式：前端生成唯一 conversationId，后续对话传入相同的 ID 即可维持上下文。
 * 典型场景："XX接口怎么调用？" → "有示例吗？" → "Python版本呢？"
 * </p>
 *
 * <p>
 * 增强功能：集成 {@link QueryRewriteService} 进行查询改写，解决指代消解问题。
 * 例如用户问"然后呢？"会被改写为完整的问题后再检索。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class MultiTurnQAService {

    private final ChatClient chatClient;
    private final InputSanitizer sanitizer;
    private final QueryRewriteService queryRewriteService;

    /**
     * 多轮对话（阻塞式）。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>Query 改写：根据对话历史补全指代词（如"它"、"这个"）</li>
     *   <li>安全清洗：通过 {@link InputSanitizer} 过滤恶意输入</li>
     *   <li>LLM 调用：携带会话历史生成回答</li>
     *   <li>记录响应：将助手回答加入对话历史，供下一轮改写使用</li>
     * </ol>
     * </p>
     *
     * @param conversationId 会话唯一标识（前端生成并维护）
     * @param question       用户当前问题
     * @return LLM 生成的回答
     */
    public String ask(String conversationId, String question) {
        // 1. Query 改写（指代消解）
        String rewrittenQuery = queryRewriteService.rewrite(conversationId, question);

        // 2. 安全清洗（防止 Prompt 注入）
        String cleaned = sanitizer.sanitize(rewrittenQuery);

        // 3. 调用 LLM（携带会话历史）
        String answer = chatClient
            .prompt()
            .user(cleaned)
            .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
            .call()
            .content();

        // 4. 记录助手回答到历史（用于下一轮 Query 改写）
        queryRewriteService.recordResponse(conversationId, answer);

        return answer;
    }

    /**
     * 多轮对话（流式 SSE）。
     * <p>
     * 与阻塞式逻辑相同，但通过 Flux 流式返回。
     * 注意：流式模式下暂不记录助手回答到历史（需要客户端确认接收完成）。
     * 如需完整支持流式的历史记录，建议在客户端收到完整回答后调用独立接口记录。
     * </p>
     *
     * @param conversationId 会话唯一标识
     * @param question       用户当前问题
     * @return 流式回答的 Flux
     */
    public Flux<String> askStream(String conversationId, String question) {
        // 1. Query 改写
        String rewrittenQuery = queryRewriteService.rewrite(conversationId, question);

        // 2. 安全清洗
        String cleaned = sanitizer.sanitize(rewrittenQuery);

        // 3. 流式调用 LLM
        return chatClient
            .prompt()
            .user(cleaned)
            .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
            .stream()
            .content();
    }
}
