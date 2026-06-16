package com.yt.knowledge.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ImportResult} 单元测试。
 */
@DisplayName("ImportResult 单元测试")
class ImportResultTest {

    private ImportResult result;

    @BeforeEach
    void setUp() {
        result = new ImportResult();
    }

    @Nested
    @DisplayName("默认值")
    class DefaultValues {

        @Test
        @DisplayName("新创建的 ImportResult → 所有数值为 0, success=false")
        void shouldHaveDefaultValues() {
            assertFalse(result.isSuccess());
            assertEquals(0, result.getFileCount());
            assertEquals(0, result.getAfterClean());
            assertEquals(0, result.getChunkCount());
            assertEquals(0, result.getElapsedMs());
            assertNull(result.getError());
        }
    }

    @Nested
    @DisplayName("成功场景")
    class SuccessScenario {

        @Test
        @DisplayName("导入成功 → success=true, error=null")
        void shouldSetSuccessState() {
            result.setSuccess(true);
            result.setFileCount(5);
            result.setAfterClean(4);
            result.setChunkCount(20);
            result.setElapsedMs(1500);

            assertTrue(result.isSuccess());
            assertEquals(5, result.getFileCount());
            assertEquals(4, result.getAfterClean());
            assertEquals(20, result.getChunkCount());
            assertEquals(1500, result.getElapsedMs());
            assertNull(result.getError());
        }
    }

    @Nested
    @DisplayName("失败场景")
    class FailureScenario {

        @Test
        @DisplayName("导入失败 → success=false, error 有值")
        void shouldSetErrorState() {
            result.setSuccess(false);
            result.setError("PDF 解析失败: test.pdf");

            assertFalse(result.isSuccess());
            assertEquals("PDF 解析失败: test.pdf", result.getError());
        }
    }

    @Nested
    @DisplayName("Lombok @Data 行为")
    class LombokDataBehavior {

        @Test
        @DisplayName("toString 包含所有关键字段")
        void shouldHaveUsefulToString() {
            result.setFileCount(10);
            result.setChunkCount(50);
            String str = result.toString();
            assertTrue(str.contains("fileCount=10"));
            assertTrue(str.contains("chunkCount=50"));
        }

        @Test
        @DisplayName("equals 基于所有字段")
        void shouldHaveCorrectEquals() {
            ImportResult r1 = new ImportResult();
            ImportResult r2 = new ImportResult();
            assertEquals(r1, r2);

            r1.setFileCount(5);
            assertNotEquals(r1, r2);
        }
    }
}
