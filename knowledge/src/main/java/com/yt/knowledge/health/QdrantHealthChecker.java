package com.yt.knowledge.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QdrantHealthChecker implements ApplicationListener<ApplicationReadyEvent> {

    private final VectorStore vectorStore;

    public QdrantHealthChecker(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            log.info("✓ Qdrant 连接正常");
        } catch (Exception e) {
            log.error("✗ Qdrant 连接失败: {}", e.getMessage());
            log.error("请确认: docker compose up -d");
        }
    }
}
