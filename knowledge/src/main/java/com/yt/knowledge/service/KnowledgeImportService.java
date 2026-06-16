package com.yt.knowledge.service;

import com.yt.knowledge.etl.ChunkSplitter;
import com.yt.knowledge.etl.DocumentCleaner;
import com.yt.knowledge.etl.DocumentLoader;
import com.yt.knowledge.etl.MetadataEnricher;
import com.yt.knowledge.model.ImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeImportService {

    private final DocumentLoader documentLoader;
    private final DocumentCleaner documentCleaner;
    private final ChunkSplitter chunkSplitter;
    private final MetadataEnricher metadataEnricher;
    private final VectorStore vectorStore;

    @Value("${app.import.batch-size:200}")
    private int batchSize;

    @Value("${app.import.parallel-threads:3}")
    private int parallelThreads;

    /**
     * 全量导入：按文件逐个处理 → 分批写入 + 并行 Embedding
     *
     * 关键：每个文件的 chunk 在 enrich() 时注入正确的文件路径作为 source，
     * 确保增量同步的 deleteBySource() 和检索溯源能正常工作。
     */
    public ImportResult fullImport(String docsDir) {
        ImportResult result = new ImportResult();
        long start = System.currentTimeMillis();

        try {
            // Step 1: 按文件分组加载
            Map<String, List<Document>> docsByFile = documentLoader.loadGroupedByFile(docsDir);

            // Step 2-4: 按文件逐个清洗 → 切片 → 元数据注入
            List<Document> allChunks = new ArrayList<>();
            int totalRaw = 0, totalCleaned = 0;

            for (var entry : docsByFile.entrySet()) {
                String filePath = entry.getKey();
                List<Document> rawDocs = entry.getValue();
                totalRaw += rawDocs.size();

                List<Document> cleaned = documentCleaner.clean(rawDocs);
                totalCleaned += cleaned.size();

                List<Document> chunks = chunkSplitter.split(cleaned);
                // 传入文件路径（而非目录路径），source 元数据正确
                List<Document> enriched = metadataEnricher.enrich(chunks, filePath);
                allChunks.addAll(enriched);
            }

            result.setFileCount(totalRaw);
            result.setAfterClean(totalCleaned);
            result.setChunkCount(allChunks.size());
            log.info("切片完成: {} 个 chunk（{} 个文件），开始分批写入...",
                    allChunks.size(), docsByFile.size());

            // Step 5: 分批写入 Qdrant
            List<List<Document>> batches = partition(allChunks, batchSize);
            AtomicInteger completed = new AtomicInteger(0);
            int totalBatches = batches.size();

            ExecutorService executor = Executors.newFixedThreadPool(parallelThreads);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < batches.size(); i++) {
                final int batchIndex = i;
                final List<Document> batch = batches.get(i);

                futures.add(executor.submit(() -> {
                    try {
                        vectorStore.add(batch);
                        int done = completed.incrementAndGet();
                        log.info("进度: {}/{} 批次 ({})", done, totalBatches,
                                batchIndex < totalBatches - 1 ? "进行中" : "完成");
                    } catch (Exception e) {
                        log.error("批次 {} 写入失败: {}", batchIndex, e.getMessage());
                        throw new RuntimeException(e);
                    }
                }));
            }

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.MINUTES);
            }
            executor.shutdown();

            result.setSuccess(true);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            log.error("知识库导入失败", e);
            throw new RuntimeException("知识库导入失败", e);
        }

        long elapsed = System.currentTimeMillis() - start;
        result.setElapsedMs(elapsed);
        log.info("导入完成: {} chunk / {} 批次，耗时 {}s",
                result.getChunkCount(),
                (int) Math.ceil((double) result.getChunkCount() / batchSize),
                String.format("%.1f", elapsed / 1000.0));
        return result;
    }

    /**
     * 单文件导入（用于增量同步）
     */
    public void importSingleFile(String filePath) {
        List<Document> docs = documentLoader.loadFile(filePath);
        List<Document> cleaned = documentCleaner.clean(docs);
        List<Document> chunks = chunkSplitter.split(cleaned);
        List<Document> enriched = metadataEnricher.enrich(chunks, filePath);

        List<List<Document>> batches = partition(enriched, batchSize);
        for (List<Document> batch : batches) {
            vectorStore.add(batch);
        }
        log.info("单文件导入完成: {} ({} chunk)", filePath, enriched.size());
    }

    /**
     * 按路径列表批量导入：支持文件和目录混传
     */
    public ImportResult importPaths(List<String> paths) {
        ImportResult result = new ImportResult();
        long start = System.currentTimeMillis();
        int totalRaw = 0, totalCleaned = 0;
        List<Document> allChunks = new ArrayList<>();

        for (String path : paths) {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) {
                log.warn("路径不存在，跳过: {}", path);
                continue;
            }
            if (f.isDirectory()) {
                // 目录 → 按文件分组处理
                try {
                    Map<String, List<Document>> docsByFile = documentLoader.loadGroupedByFile(path);
                    for (var entry : docsByFile.entrySet()) {
                        String filePath = entry.getKey();
                        List<Document> rawDocs = entry.getValue();
                        totalRaw += rawDocs.size();
                        List<Document> cleaned = documentCleaner.clean(rawDocs);
                        totalCleaned += cleaned.size();
                        List<Document> chunks = chunkSplitter.split(cleaned);
                        List<Document> enriched = metadataEnricher.enrich(chunks, filePath);
                        allChunks.addAll(enriched);
                    }
                } catch (Exception e) {
                    log.error("目录导入失败: {}", path, e);
                }
            } else {
                // 单个文件
                List<Document> docs = documentLoader.loadFile(path);
                totalRaw += docs.size();
                List<Document> cleaned = documentCleaner.clean(docs);
                totalCleaned += cleaned.size();
                List<Document> chunks = chunkSplitter.split(cleaned);
                List<Document> enriched = metadataEnricher.enrich(chunks, path);
                allChunks.addAll(enriched);
            }
        }

        result.setFileCount(totalRaw);
        result.setAfterClean(totalCleaned);
        result.setChunkCount(allChunks.size());

        // 分批写入
        List<List<Document>> batches = partition(allChunks, batchSize);
        for (int i = 0; i < batches.size(); i++) {
            vectorStore.add(batches.get(i));
            log.info("进度: {}/{} 批次", i + 1, batches.size());
        }

        long elapsed = System.currentTimeMillis() - start;
        result.setElapsedMs(elapsed);
        result.setSuccess(true);
        log.info("批量路径导入完成: {} chunk / {} 批次，耗时 {}s",
                allChunks.size(), batches.size(), String.format("%.1f", elapsed / 1000.0));
        return result;
    }

    /**
     * 将列表按 batchSize 分区
     *
     * 性能备注：vectorStore.add(batch) 底层逐条调用 Ollama /api/embeddings。
     * Ollama >= 0.1.26 支持批量 Embedding（POST /api/embed, body: {"input": ["text1","text2",...]}），
     * 一次 HTTP 调用嵌入整个 batch，可减少一个数量级的网络往返。
     * Spring AI 1.0 是否自动启用批量 Embedding 取决于 OllamaApi 实现版本。
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
