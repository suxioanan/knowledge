package com.yt.knowledge.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KnowledgeMetrics} 单元测试。
 * <p>
 * 验证 Micrometer 指标注册和更新。
 * </p>
 */
@DisplayName("KnowledgeMetrics 单元测试")
class KnowledgeMetricsTest {

    private MeterRegistry registry;
    private KnowledgeMetrics metrics;

    @BeforeEach
    void setUp() {
        // 使用 SimpleMeterRegistry（内存中，无需 Prometheus）
        registry = new SimpleMeterRegistry();
        metrics = new KnowledgeMetrics(registry);
    }

    @Nested
    @DisplayName("recordImport")
    class RecordImport {

        @Test
        @DisplayName("调用后 Gauge 更新为传入的 chunkCount")
        void shouldUpdateChunkGauge() {
            metrics.recordImport(100);

            var gauge = registry.find("knowledge.chunks.total").gauge();
            assertNotNull(gauge);
            assertEquals(100.0, gauge.value(), 0.01);
        }

        @Test
        @DisplayName("多次调用 → Counter 递增")
        void shouldIncrementImportCounter() {
            metrics.recordImport(50);
            metrics.recordImport(30);
            metrics.recordImport(20);

            double count = registry.find("knowledge.import.count").counter().count();
            assertEquals(3.0, count, 0.01);

            // Gauge 值为最后一次设置的值
            var gauge = registry.find("knowledge.chunks.total").gauge();
            assertEquals(20.0, gauge.value(), 0.01);
        }

        @Test
        @DisplayName("chunkCount=0 → Gauge 为 0")
        void shouldHandleZeroChunks() {
            metrics.recordImport(0);

            var gauge = registry.find("knowledge.chunks.total").gauge();
            assertEquals(0.0, gauge.value(), 0.01);
        }
    }

    @Nested
    @DisplayName("recordSearch")
    class RecordSearch {

        @Test
        @DisplayName("带分类 → Counter 带 category 标签")
        void shouldRecordSearchWithCategory() {
            metrics.recordSearch("api");
            metrics.recordSearch("api");
            metrics.recordSearch("database");

            double apiCount = registry.find("knowledge.search.count")
                .tag("category", "api").counter().count();
            double dbCount = registry.find("knowledge.search.count")
                .tag("category", "database").counter().count();

            assertEquals(2.0, apiCount, 0.01);
            assertEquals(1.0, dbCount, 0.01);
        }

        @Test
        @DisplayName("null 分类 → 记为 'all'")
        void shouldUseAllForNullCategory() {
            metrics.recordSearch(null);

            double count = registry.find("knowledge.search.count")
                .tag("category", "all").counter().count();
            assertEquals(1.0, count, 0.01);
        }
    }
}
