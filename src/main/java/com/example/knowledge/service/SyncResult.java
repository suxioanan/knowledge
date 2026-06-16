package com.example.knowledge.service;

import lombok.Data;

@Data
public class SyncResult {
    private int added;
    private int updated;
    private int deleted;
    private int skipped;

    public void incrementAdded()   { added++; }
    public void incrementUpdated() { updated++; }
    public void incrementDeleted() { deleted++; }
    public void incrementSkipped() { skipped++; }
}
