package com.example.knowledge.etl;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChunkSplitter {

    private final TokenTextSplitter splitter;

    public ChunkSplitter() {
        this.splitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(50)
            .withKeepSeparator(true)
            .build();
    }

    public List<Document> split(List<Document> documents) {
        return splitter.apply(documents);
    }
}
