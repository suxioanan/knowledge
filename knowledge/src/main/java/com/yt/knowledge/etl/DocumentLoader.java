package com.yt.knowledge.etl;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class DocumentLoader {

    private final MarkdownCleaner markdownCleaner;

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("pdf", "docx", "doc", "md", "txt", "ppt", "pptx", "xls", "xlsx", "html");

    private static final Set<String> EXCLUDE_DIRS =
            Set.of("archive", ".git", "node_modules");

//    /**
//     * 扫描目录，按文件类型分发到对应的 Reader
//     */
//    public List<Document> loadFromDirectory(String dirPath) throws IOException {
//        List<Document> allDocuments = new ArrayList<>();
//
//        try (Stream<Path> paths = Files.walk(Paths.get(dirPath))) {
//            paths.filter(Files::isRegularFile)
//                 .filter(this::isSupported)
//                 .filter(this::notExcluded)
//                 .forEach(file -> {
//                     try {
//                         List<Document> docs = loadFile(file.toFile().getAbsolutePath());
//                         allDocuments.addAll(docs);
//                         System.out.printf("✓ 已加载: %s (%d 段)%n",
//                                 file.getFileName(), docs.size());
//                     } catch (Exception e) {
//                         System.err.printf("✗ 加载失败: %s — %s%n",
//                                 file.getFileName(), e.getMessage());
//                     }
//                 });
//        }
//        return allDocuments;
//    }

    /**
     * 加载单个文件
     */
    public List<Document> loadFile(String filePath) {
        String ext = getExtension(filePath).toLowerCase();
        Resource resource = new FileSystemResource(filePath);

        return switch (ext) {
            case "pdf" -> new ParagraphPdfDocumentReader(resource).get();
            case "docx", "doc", "ppt", "pptx", "xls", "xlsx", "html" ->
                    new TikaDocumentReader(resource).get();
            case "md" -> {
                List<Document> docs = new TextReader(resource).get();
                for (int i = 0; i < docs.size(); i++) {
                    Document doc = docs.get(i);
                    String cleaned = markdownCleaner.clean(doc.getText());
                    docs.set(i, new Document(cleaned, doc.getMetadata()));
                }
                yield docs;
            }
            case "txt" -> new TextReader(resource).get();
            default -> throw new IllegalArgumentException("不支持的文件格式: " + ext);
        };
    }

    /**
     * 按文件路径分组加载（生产推荐）
     * 返回 Map<文件绝对路径, 该文件的 Document 列表>，用于后续按文件注入正确的 source 元数据
     */
    public Map<String, List<Document>> loadGroupedByFile(String dirPath) throws IOException {
        Map<String, List<Document>> result = new LinkedHashMap<>();

        try (Stream<Path> paths = Files.walk(Paths.get(dirPath))) {
            paths.filter(Files::isRegularFile)
                 .filter(this::isSupported)
                 .filter(this::notExcluded)
                 .forEach(file -> {
                     try {
                         String absPath = file.toAbsolutePath().toString();
                         List<Document> docs = loadFile(absPath);
                         result.put(absPath, docs);
                         System.out.printf("✓ 已加载: %s (%d 段)%n",
                                 file.getFileName(), docs.size());
                     } catch (Exception e) {
                         System.err.printf("✗ 加载失败: %s — %s%n",
                                 file.getFileName(), e.getMessage());
                     }
                 });
        }
        return result;
    }

    private boolean isSupported(Path path) {
        return SUPPORTED_EXTENSIONS.contains(getExtension(path.toString()));
    }

    private boolean notExcluded(Path path) {
        for (int i = 0; i < path.getNameCount(); i++) {
            if (EXCLUDE_DIRS.contains(path.getName(i).toString())) {
                return false;
            }
        }
        return true;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "" : filename.substring(dot + 1);
    }
}
