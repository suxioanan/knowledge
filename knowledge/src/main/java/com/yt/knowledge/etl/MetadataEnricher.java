package com.yt.knowledge.etl;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class MetadataEnricher {

    public List<Document> enrich(List<Document> documents, String sourceFile) {
        Path path = Path.of(sourceFile);
        String fileName = path.getFileName().toString();
        Instant now = Instant.now();

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            doc.getMetadata().put("source", sourceFile);
            doc.getMetadata().put("file_name", fileName);
            doc.getMetadata().put("file_type", extension(fileName));
            doc.getMetadata().put("chunk_index", i);
            doc.getMetadata().put("chunk_total", documents.size());
            doc.getMetadata().put("doc_id", UUID.randomUUID().toString());
            doc.getMetadata().put("imported_at", now.toString());
            doc.getMetadata().put("category", guessCategory(sourceFile));
        }
        return documents;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "unknown" : filename.substring(dot + 1).toLowerCase();
    }

    private String guessCategory(String filePath) {
        if (filePath.contains("/api/"))       return "api";
        if (filePath.contains("/database/"))  return "database";
        if (filePath.contains("/product/"))   return "product";
        if (filePath.contains("/wiki/"))      return "wiki";
        return "other";
    }
}
