package com.yt.knowledge.controller;

import com.yt.knowledge.model.ImportResult;
import com.yt.knowledge.service.KnowledgeImportService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeImportService importService;

    /**
     * 全量导入指定目录下的所有文档
     * POST /api/knowledge/import?dir=docs
     * POST /api/knowledge/import?dir=/Users/xxx/my-docs
     */
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult importDocs(@RequestParam(defaultValue = "docs") String dir) {
        // 验证路径合法性，防止目录穿越
        if (!isValidPath(dir)) {
            throw new IllegalArgumentException("非法的目录路径");
        }
        return importService.fullImport(dir);
    }

    /**
     * 导入单个文件
     * POST /api/knowledge/import-file?path=/Users/xxx/docs/api/order.md
     */
    @PostMapping("/import-file")
    @PreAuthorize("hasRole('ADMIN')")
    public String importFile(@RequestParam String path) {
        // 验证路径合法性
        if (!isValidPath(path)) {
            throw new IllegalArgumentException("非法的文件路径");
        }
        importService.importSingleFile(path);
        return "OK";
    }

    /**
     * 按指定路径列表批量导入（支持文件和目录混传）
     * POST /api/knowledge/import-paths
     * Body: {"paths": ["/Users/xxx/docs/api", "/Users/xxx/docs/database/redis.md"]}
     */
    @PostMapping("/import-paths")
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult importPaths(@RequestBody ImportPathsRequest request) {
        // 验证所有路径
        for (String path : request.getPaths()) {
            if (!isValidPath(path)) {
                throw new IllegalArgumentException("非法的路径: " + path);
            }
        }
        return importService.importPaths(request.getPaths());
    }

    /**
     * 验证路径合法性，防止目录穿越攻击
     */
    private boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // 禁止包含 ".." 防止目录穿越
        if (path.contains("..")) {
            return false;
        }
        // 如果是相对路径，确保在允许的基础目录下
        java.io.File file = new java.io.File(path);
        try {
            String canonicalPath = file.getCanonicalPath();
            // 允许当前工作目录及其子目录
            String userDir = System.getProperty("user.dir");
            return canonicalPath.equals(userDir)
                || canonicalPath.startsWith(userDir + java.io.File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    @Data
    public static class ImportPathsRequest {
        private List<String> paths;
    }
}
