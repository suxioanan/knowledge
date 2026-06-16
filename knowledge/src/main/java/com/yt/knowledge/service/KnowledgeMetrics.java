package com.yt.knowledge.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class KnowledgeMetrics {

    private final MeterRegistry registry;
    private final AtomicLong totalChunks;

    public KnowledgeMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.totalChunks = registry.gauge("knowledge.chunks.total", new AtomicLong(0));
    }

    public void recordImport(int chunkCount) {
        if (totalChunks != null) {
            totalChunks.set(chunkCount);
        }
        registry.counter("knowledge.import.count").increment();
    }

    public void recordSearch(String category) {
        registry.counter("knowledge.search.count",
            "category", category != null ? category : "all").increment();
    }
}
