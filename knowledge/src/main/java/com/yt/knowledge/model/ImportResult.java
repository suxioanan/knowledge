package com.yt.knowledge.model;

import lombok.Data;

@Data
public class ImportResult {
    private boolean success;
    private int fileCount;
    private int afterClean;
    private int chunkCount;
    private long elapsedMs;
    private String error;
}
