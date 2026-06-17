package com.yt.knowledge.model;

import lombok.Data;

/**
 * 知识库导入结果 DTO。
 * <p>
 * 记录一次导入操作的统计信息，用于 API 返回和日志输出。
 * </p>
 */
@Data
public class ImportResult {
    /** 导入是否成功 */
    private boolean success;
    /** 处理的文件数 */
    private long fileCount;
    /** 清洗后保留的文档数 */
    private long afterClean;
    /** 最终的 Chunk 总数 */
    private long chunkCount;
    /** 导入耗时（毫秒） */
    private long elapsedMs;
    /** 错误信息（仅在 success=false 时有值） */
    private String error;
}
