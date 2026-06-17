package com.yt.knowledge.etl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 文档加载器。
 * <p>
 * 根据文件扩展名自动选择合适的 Spring AI Reader 加载文档内容。
 * 支持 PDF、Office（Word/PPT/Excel）、Markdown、TXT、HTML 等常见格式。
 * </p>
 *
 * <h3>支持的格式</h3>
 * <ul>
 *   <li><b>PDF</b> → {@link ParagraphPdfDocumentReader}（按段落解析）</li>
 *   <li><b>Office</b>（docx/doc/ppt/pptx/xls/xlsx）→ {@link TikaDocumentReader}（Apache Tika 通用解析）</li>
 *   <li><b>HTML</b> → {@link TikaDocumentReader}</li>
 *   <li><b>Markdown</b> → {@link TextReader} + {@link MarkdownCleaner} 清洗</li>
 *   <li><b>TXT</b> → {@link TextReader}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentLoader {

    private final MarkdownCleaner markdownCleaner;

    /** 支持的文件扩展名（不含点） */
    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("pdf", "docx", "doc", "md", "txt", "ppt", "pptx", "xls", "xlsx", "html");

    /** 扫描时需要排除的目录名 */
    private static final Set<String> EXCLUDE_DIRS =
            Set.of("archive", ".git", "node_modules");

    /**
     * 加载单个文件。
     * <p>
     * 策略模式：根据扩展名（switch expression）分发到不同的 Spring AI Reader。
     * Markdown 文件额外经过 {@link MarkdownCleaner} 清洗。
     * </p>
     *
     * @param filePath 文件绝对路径
     * @return 文档段落列表
     * @throws IllegalArgumentException 如果文件格式不支持
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
     * 按文件路径分组加载（生产推荐方式）。
     * <p>
     * 递归扫描目录，按文件逐个加载，返回 {@code Map<文件绝对路径, Document列表>}。
     * 这种分组方式确保后续的 {@link MetadataEnricher#enrich} 能按文件注入正确的 source 路径。
     * </p>
     *
     * @param dirPath 文档根目录
     * @return 文件路径 → 该文件的 Document 列表（LinkedHashMap 保持扫描顺序）
     * @throws IOException 如果遍历目录失败
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
                         log.info("✓ 已加载: {} ({} 段)", file.getFileName(), docs.size());
                     } catch (Exception e) {
                         log.warn("✗ 加载失败: {} — {}", file.getFileName(), e.getMessage());
                     }
                 });
        }
        return result;
    }

    /**
     * 判断文件扩展名是否在支持列表中。
     */
    private boolean isSupported(Path path) {
        return SUPPORTED_EXTENSIONS.contains(getExtension(path.toString()));
    }

    /**
     * 判断路径是否不在排除目录中。
     * <p>
     * 检查路径的每一级目录名，只要有一级匹配排除列表就视为排除。
     * </p>
     */
    private boolean notExcluded(Path path) {
        for (int i = 0; i < path.getNameCount(); i++) {
            if (EXCLUDE_DIRS.contains(path.getName(i).toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取文件扩展名（不含点）。
     *
     * @param filename 文件名或路径
     * @return 小写扩展名，无扩展名时返回空字符串
     */
    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "" : filename.substring(dot + 1);
    }
}
