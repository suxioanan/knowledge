package com.yt.knowledge.service;

import com.yt.knowledge.model.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 增量同步服务。
 * <p>
 * 通过 MD5 哈希比对检测文档文件变更，实现精准的增量同步：
 * </p>
 * <ol>
 *   <li><b>新增文件</b>：旧哈希为 null → 全量导入</li>
 *   <li><b>修改文件</b>：哈希不一致 → 先删除旧 Chunk，再导入新内容</li>
 *   <li><b>删除文件</b>：索引中存在但磁盘不存在 → 从向量库移除</li>
 *   <li><b>无变化</b>：哈希一致 → 跳过</li>
 * </ol>
 *
 * <h3>索引持久化</h3>
 * 支持通过 {@link #saveIndex(String)} / {@link #loadIndex(String)} 将哈希索引持久化到 Properties 文件，
 * 避免服务重启后全量重新索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncrementalSyncService {

    private final KnowledgeImportService importService;
    private final VectorStore vectorStore;

    /** 文件路径 → MD5 哈希的内存索引（线程安全） */
    private final Map<String, String> fileHashIndex = new ConcurrentHashMap<>();

    /**
     * 执行增量同步。
     * <p>
     * 遍历目标目录下所有非隐藏文件，逐个对比 MD5 哈希完成同步决策。
     * </p>
     *
     * @param docsDir 文档目录路径
     * @return 同步结果（新增/修改/删除/跳过的数量）
     * @throws IOException 如果目录遍历失败
     */
    public SyncResult sync(String docsDir) throws IOException {
        SyncResult result = new SyncResult();
        Set<String> currentFiles = new HashSet<>();

        // 遍历目录，检测新增和修改
        try (Stream<Path> paths = Files.walk(Paths.get(docsDir))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> !p.getFileName().toString().startsWith("."))  // 排除隐藏文件
                 .forEach(path -> {
                     String filePath = path.toAbsolutePath().toString();
                     currentFiles.add(filePath);
                     String newHash = md5(filePath);
                     String oldHash = fileHashIndex.get(filePath);

                     if (oldHash == null) {
                         // 新增：旧哈希不存在
                         importService.importSingleFile(filePath);
                         fileHashIndex.put(filePath, newHash);
                         result.incrementAdded();
                     } else if (!oldHash.equals(newHash)) {
                         // 修改：哈希不一致 → 删旧入新
                         deleteBySource(filePath);
                         importService.importSingleFile(filePath);
                         fileHashIndex.put(filePath, newHash);
                         result.incrementUpdated();
                     } else {
                         // 未变化：跳过
                         result.incrementSkipped();
                     }
                 });
        }

        // 检测已删除的文件：索引中有但磁盘上已不存在
        Set<String> deletedFiles = new HashSet<>(fileHashIndex.keySet());
        deletedFiles.removeAll(currentFiles);
        for (String deleted : deletedFiles) {
            deleteBySource(deleted);
            fileHashIndex.remove(deleted);
            result.incrementDeleted();
        }

        return result;
    }

    /**
     * 计算文件的 MD5 哈希值。
     * <p>
     * 使用 8KB 缓冲区流式读取，避免大文件占用过多内存。
     * </p>
     *
     * @param filePath 文件路径
     * @return 32 位十六进制 MD5 字符串
     * @throws RuntimeException 如果文件读取或摘要计算失败
     */
    private String md5(String filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = new FileInputStream(filePath)) {
                byte[] buffer = new byte[8192];  // 8KB 缓冲区
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败: " + filePath, e);
        }
    }

    /**
     * 根据 source 元数据删除向量库中该文件的所有 Chunk。
     * <p>
     * 使用 Qdrant Filter：{@code source == filePath}。
     * </p>
     *
     * @param filePath 源文件的绝对路径（与 MetadataEnricher 注入的 source 字段一致）
     */
    private void deleteBySource(String filePath) {
        vectorStore.delete(
            new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key("source"), new Filter.Value(filePath)));
    }

    /**
     * 将哈希索引持久化到 Properties 文件。
     *
     * @param indexPath 索引文件路径
     * @throws IOException 如果写入失败
     */
    public void saveIndex(String indexPath) throws IOException {
        Properties props = new Properties();
        props.putAll(fileHashIndex);
        try (OutputStream os = new FileOutputStream(indexPath)) {
            props.store(os, "Knowledge base file hash index");
        }
    }

    /**
     * 从 Properties 文件加载哈希索引到内存。
     *
     * @param indexPath 索引文件路径
     * @throws IOException 如果读取失败
     */
    public void loadIndex(String indexPath) throws IOException {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(indexPath)) {
            props.load(is);
        }
        props.forEach((k, v) -> fileHashIndex.put((String) k, (String) v));
    }
}
