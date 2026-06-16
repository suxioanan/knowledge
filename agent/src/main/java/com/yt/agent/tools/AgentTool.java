package com.yt.agent.tools;

/**
 * Agent 工具标记接口。
 * 所有需要注册给 ChatClient 的 {@code @Tool} 类必须实现此接口。
 * Spring 会自动收集所有 AgentTool 实现类，统一注册。
 *
 * 扩展示例：
 * <pre>{@code
 * @Component
 * public class DatabaseTools implements AgentTool {
 *     @Tool(name = "queryData", description = "...")
 *     public String queryData(@ToolParam(description = "...") String param) {
 *         // ...
 *     }
 * }
 * }</pre>
 */
public interface AgentTool {
}
