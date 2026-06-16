package com.yt.knowledge.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class IncrementalSyncService {

    private final KnowledgeImportService importService;
    private final VectorStore vectorStore;

    private final Map<String, String> fileHashIndex = new ConcurrentHashMap<>();

    public SyncResult sync(String docsDir) throws IOException {
        SyncResult result = new SyncResult();
        Set<String> currentFiles = new HashSet<>();

        try (Stream<Path> paths = Files.walk(Paths.get(docsDir))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> !p.getFileName().toString().startsWith("."))
                 .forEach(path -> {
                     String filePath = path.toAbsolutePath().toString();
                     currentFiles.add(filePath);
                     String newHash = md5(filePath);
                     String oldHash = fileHashIndex.get(filePath);

                     if (oldHash == null) {
                         importService.importSingleFile(filePath);
                         fileHashIndex.put(filePath, newHash);
                         result.incrementAdded();
                     } else if (!oldHash.equals(newHash)) {
                         deleteBySource(filePath);
                         importService.importSingleFile(filePath);
                         fileHashIndex.put(filePath, newHash);
                         result.incrementUpdated();
                     } else {
                         result.incrementSkipped();
                     }
                 });
        }

        // 检测已删除的文件
        Set<String> deletedFiles = new HashSet<>(fileHashIndex.keySet());
        deletedFiles.removeAll(currentFiles);
        for (String deleted : deletedFiles) {
            deleteBySource(deleted);
            fileHashIndex.remove(deleted);
            result.incrementDeleted();
        }

        return result;
    }

    private String md5(String filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = new FileInputStream(filePath)) {
                byte[] buffer = new byte[8192];
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

    private void deleteBySource(String filePath) {
        vectorStore.delete(
            new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key("source"), new Filter.Value(filePath)));
    }

    public void saveIndex(String indexPath) throws IOException {
        Properties props = new Properties();
        props.putAll(fileHashIndex);
        try (OutputStream os = new FileOutputStream(indexPath)) {
            props.store(os, "Knowledge base file hash index");
        }
    }

    public void loadIndex(String indexPath) throws IOException {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(indexPath)) {
            props.load(is);
        }
        props.forEach((k, v) -> fileHashIndex.put((String) k, (String) v));
    }
}
