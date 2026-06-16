package com.yt.knowledge.etl;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentCleaner {

    public List<Document> clean(List<Document> documents) {
        return documents.stream()
            .map(this::cleanDocument)
            .filter(doc -> !doc.getText().isBlank())
            .filter(doc -> doc.getText().length() >= 30)
            .toList();
    }

    private Document cleanDocument(Document doc) {
        String text = doc.getText();

        text = text.replaceAll("(?i)第\\s*\\d+\\s*页(\\s*共\\s*\\d+\\s*页)?", "");
        text = text.replaceAll("(?i)copyright\\s*©?\\s*20\\d{2}.*", "");
        text = text.replaceAll("(?i)all rights reserved\\.?", "");
        text = text.replaceAll("^\\d{1,3}\\s*$", "");
        text = text.replaceAll("\\s+", " ").trim();

        return new Document(text, doc.getMetadata());
    }
}
