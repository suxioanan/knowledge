package com.yt.knowledge.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SyncResult} 单元测试。
 * <p>
 * 验证增量同步结果的计数逻辑正确性和初始状态。
 * </p>
 */
@DisplayName("SyncResult 单元测试")
class SyncResultTest {

    private SyncResult result;

    @BeforeEach
    void setUp() {
        result = new SyncResult();
    }

    @Nested
    @DisplayName("初始状态")
    class InitialState {

        @Test
        @DisplayName("新创建的 SyncResult → 所有计数为 0")
        void shouldHaveZeroCounts() {
            assertEquals(0, result.getAdded());
            assertEquals(0, result.getUpdated());
            assertEquals(0, result.getDeleted());
            assertEquals(0, result.getSkipped());
        }
    }

    @Nested
    @DisplayName("自增方法")
    class IncrementMethods {

        @Test
        @DisplayName("incrementAdded() → added 递增")
        void shouldIncrementAdded() {
            result.incrementAdded();
            result.incrementAdded();
            assertEquals(2, result.getAdded());
            // 其他计数不应变化
            assertEquals(0, result.getUpdated());
        }

        @Test
        @DisplayName("incrementUpdated() → updated 递增")
        void shouldIncrementUpdated() {
            result.incrementUpdated();
            result.incrementUpdated();
            result.incrementUpdated();
            assertEquals(3, result.getUpdated());
        }

        @Test
        @DisplayName("incrementDeleted() → deleted 递增")
        void shouldIncrementDeleted() {
            result.incrementDeleted();
            assertEquals(1, result.getDeleted());
        }

        @Test
        @DisplayName("incrementSkipped() → skipped 递增")
        void shouldIncrementSkipped() {
            for (int i = 0; i < 10; i++) {
                result.incrementSkipped();
            }
            assertEquals(10, result.getSkipped());
        }

        @Test
        @DisplayName("各方法独立计数，互不影响")
        void shouldHaveIndependentCounters() {
            result.incrementAdded();     // added=1
            result.incrementUpdated();   // updated=1
            result.incrementUpdated();   // updated=2
            result.incrementDeleted();   // deleted=1
            result.incrementSkipped();   // skipped=1
            result.incrementSkipped();   // skipped=2

            assertEquals(1, result.getAdded());
            assertEquals(2, result.getUpdated());
            assertEquals(1, result.getDeleted());
            assertEquals(2, result.getSkipped());
        }
    }

    @Nested
    @DisplayName("Lombok @Data 行为")
    class LombokDataBehavior {

        @Test
        @DisplayName("setter/getter 正常工作")
        void shouldSupportSetterGetter() {
            result.setAdded(5);
            result.setUpdated(3);
            result.setDeleted(2);
            result.setSkipped(100);

            assertEquals(5, result.getAdded());
            assertEquals(3, result.getUpdated());
            assertEquals(2, result.getDeleted());
            assertEquals(100, result.getSkipped());
        }

        @Test
        @DisplayName("equals/hashCode 基于全部字段")
        void shouldHaveCorrectEquality() {
            SyncResult r1 = new SyncResult();
            SyncResult r2 = new SyncResult();

            assertEquals(r1, r2);
            assertEquals(r1.hashCode(), r2.hashCode());

            r1.incrementAdded();
            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("toString 包含所有字段")
        void shouldHaveUsefulToString() {
            result.incrementAdded();
            result.incrementSkipped();
            String str = result.toString();
            assertTrue(str.contains("added=1"));
            assertTrue(str.contains("skipped=1"));
        }
    }
}
