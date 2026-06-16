package com.yt.knowledge.model;

import lombok.Data;

/**
 * 增量同步结果 DTO。
 * <p>
 * 记录增量同步操作中各类变更的计数。
 * 提供了自增方法供 {@code IncrementalSyncService} 在流式处理中累加计数。
 * </p>
 *
 * <p>
 * 注意：increment 方法未添加同步保护，
 * 在并行调用场景下（如 forEach 多线程）可能存在竞态条件。
 * 当前 {@code IncrementalSyncService.sync()} 的 forEach 在单线程 Stream 中执行，
 * 暂时安全，但未来若改为并行流需补充同步机制。
 * </p>
 */
@Data
public class SyncResult {
    /** 新增文件数 */
    private int added;
    /** 修改文件数（内容变更） */
    private int updated;
    /** 删除文件数（磁盘上已不存在） */
    private int deleted;
    /** 跳过的文件数（内容未变化） */
    private int skipped;

    /** 新增计数 +1 */
    public void incrementAdded()   { added++; }
    /** 修改计数 +1 */
    public void incrementUpdated() { updated++; }
    /** 删除计数 +1 */
    public void incrementDeleted() { deleted++; }
    /** 跳过计数 +1 */
    public void incrementSkipped() { skipped++; }
}
