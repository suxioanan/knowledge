package com.yt.knowledge.service;

import com.yt.knowledge.model.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.sync", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SyncScheduler {

    private final IncrementalSyncService syncService;

    @Scheduled(cron = "${app.sync.cron:0 0 3 * * *}")
    public void nightlySync() throws IOException {
        SyncResult result = syncService.sync("docs");
        log.info("增量同步完成: 新增{} 更新{} 删除{} 跳过{}",
                result.getAdded(), result.getUpdated(),
                result.getDeleted(), result.getSkipped());
    }
}
