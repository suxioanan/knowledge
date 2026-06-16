package com.example.knowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final IncrementalSyncService syncService;

    @Scheduled(cron = "0 0 3 * * *")
    public void nightlySync() throws IOException {
        SyncResult result = syncService.sync("docs");
        log.info("增量同步完成: 新增{} 更新{} 删除{} 跳过{}",
                result.getAdded(), result.getUpdated(),
                result.getDeleted(), result.getSkipped());
    }
}
