package com.yt.knowledge.controller;

import com.yt.knowledge.service.SyncResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.yt.knowledge.service.IncrementalSyncService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge/admin")
@RequiredArgsConstructor
public class KnowledgeAdminController {

    private final VectorStore vectorStore;
    private final IncrementalSyncService syncService;

    @GetMapping("/check")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> checkFile(@RequestParam String source) {
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .filterExpression("source == '" + source + "'")
                .topK(1)
                .build());
        return Map.of(
            "source", source,
            "exists", !docs.isEmpty(),
            "sampleChunk", docs.isEmpty() ? "" :
                docs.get(0).getText().substring(0, Math.min(100, docs.get(0).getText().length())));
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> deleteBySource(@RequestParam String source) {
        vectorStore.delete(
            new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key("source"), new Filter.Value(source)));
        return Map.of("source", source, "deleted", true);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> stats() {
        return Map.of(
            "collection", "knowledge",
            "tip", "精确统计请直接调用 Qdrant API: GET /collections/knowledge"
        );
    }

    /**
     * 手动触发增量同步
     * POST /api/knowledge/admin/sync?dir=docs
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public SyncResult manualSync(@RequestParam(defaultValue = "docs") String dir) throws IOException {
        return syncService.sync(dir);
    }
}
