package com.yt.knowledge.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 知识库业务指标采集器。
 * <p>
 * 通过 Micrometer 暴露自定义指标供 Prometheus + Grafana 监控：
 * </p>
 * <ul>
 *   <li><b>knowledge.chunks.total</b>（Gauge）：当前知识库中的 Chunk 总数</li>
 *   <li><b>knowledge.import.count</b>（Counter）：累计导入次数</li>
 *   <li><b>knowledge.search.count</b>（Counter，含 category 标签）：按分类统计的检索次数</li>
 * </ul>
 */
@Component
public class KnowledgeMetrics {

    private final MeterRegistry registry;
    private final AtomicLong totalChunks;

    public KnowledgeMetrics(MeterRegistry registry) {
        this.registry = registry;
        // 注册 Gauge 指标，通过 AtomicLong 动态更新值
        this.totalChunks = registry.gauge("knowledge.chunks.total", new AtomicLong(0));
    }

    /**
     * 记录一次导入操作。
     *
     * @param chunkCount 本次导入的 Chunk 总数（设置为 Gauge 的当前值）
     */
    public void recordImport(int chunkCount) {
        if (totalChunks != null) {
            totalChunks.set(chunkCount);
        }
        registry.counter("knowledge.import.count").increment();
    }

    /**
     * 记录一次检索操作。
     *
     * @param category 检索所属分类（null 时记为 "all"），用于按分类统计检索频率
     */
    public void recordSearch(String category) {
        registry.counter("knowledge.search.count",
            "category", category != null ? category : "all").increment();
    }
}
