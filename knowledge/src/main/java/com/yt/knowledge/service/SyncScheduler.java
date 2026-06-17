package com.yt.knowledge.service;

import com.yt.knowledge.model.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时增量同步调度器。
 * <p>
 * 在配置 {@code app.sync.enabled=true} 时启用，默认每天凌晨 3:00 执行一次，
 * 扫描 {@code docs/} 目录检测文件变更并自动同步到向量库。
 * </p>
 *
 * <p>
 * Cron 表达式可通过 {@code app.sync.cron} 配置自定义，默认为 {@code 0 0 3 * * *}。
 * 手动同步可通过 {@code POST /api/knowledge/admin/sync?dir=docs} 触发。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.sync", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SyncScheduler {

    private final IncrementalSyncService syncService;

    /**
     * 夜间定时增量同步任务。
     * <p>
     * 扫描 docs 目录，对比 MD5 哈希检测文件变更（新增/修改/删除），
     * 自动同步到 Qdrant 向量库。
     * </p>
     */
    @Value("${app.import.docs-dir:docs}")
    private String docsDir;

    @Scheduled(cron = "${app.sync.cron:0 0 3 * * *}")
    public void nightlySync() {
        try {
            SyncResult result = syncService.sync(docsDir);
            log.info("增量同步完成: 新增{} 更新{} 删除{} 跳过{}",
                    result.getAdded(), result.getUpdated(),
                    result.getDeleted(), result.getSkipped());
        } catch (Exception e) {
            log.error("增量同步失败: {}", e.getMessage(), e);
            // 不抛出异常，保证下次调度正常执行
        }
    }
}
