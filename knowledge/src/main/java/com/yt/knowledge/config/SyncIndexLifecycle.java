package com.yt.knowledge.config;

import com.yt.knowledge.service.IncrementalSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 增量同步索引生命周期管理。
 * SyncIndexLifecycle 是一个性能优化组件，通过持久化文件哈希索引，实现：
 * ✅ 智能增量同步：只处理真正变化的文件
 * ✅ 快速重启：避免无意义的全量重新索引
 * ✅ 自动化管理：无需手动调用，应用启停时自动处理
 * 对于大型知识库（数千个文档），这个优化可以节省大量时间和计算资源！🎉
 * <p>
 * 在应用启动时加载哈希索引到内存，避免重启后全量重新同步。
 * 在应用关闭时保存哈希索引到磁盘，保证数据持久化。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>应用启动 → 从 sync-index.properties 加载索引</li>
 *   <li>应用运行 → 增量同步更新内存中的索引</li>
 *   <li>应用关闭 → 将最新索引保存到 sync-index.properties</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncIndexLifecycle implements ApplicationListener<ApplicationEvent> {

    private final IncrementalSyncService syncService;

    @Value("${app.sync.index-path:sync-index.properties}")
    private String indexPath;

    /** 防止 ContextRefreshedEvent 重复触发 */
    private volatile boolean loaded = false;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent && !loaded) {
            loaded = true;
            try {
                syncService.loadIndex(indexPath);
                log.info("✅ 增量同步索引已加载: {}", indexPath);
            } catch (IOException e) {
                log.warn("⚠️ 加载增量同步索引失败（首次启动或文件不存在）: {}", e.getMessage());
            }
        } else if (event instanceof ContextClosedEvent) {
            // 应用关闭时保存索引
            try {
                syncService.saveIndex(indexPath);
                log.info("✅ 增量同步索引已保存: {}", indexPath);
            } catch (IOException e) {
                log.error("❌ 保存增量同步索引失败: {}", e.getMessage(), e);
            }
        }
    }
}
