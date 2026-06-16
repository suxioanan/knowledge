package com.yt.knowledge.controller;

import com.yt.knowledge.service.ImportResult;
import com.yt.knowledge.service.KnowledgeImportService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
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
        return importService.fullImport(dir);
    }

    /**
     * 导入单个文件
     * POST /api/knowledge/import-file?path=/Users/xxx/docs/api/order.md
     */
    @PostMapping("/import-file")
    @PreAuthorize("hasRole('ADMIN')")
    public String importFile(@RequestParam String path) {
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
        return importService.importPaths(request.getPaths());
    }

    @Data
    public static class ImportPathsRequest {
        private List<String> paths;
    }
}
