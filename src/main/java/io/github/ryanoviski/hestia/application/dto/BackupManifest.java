package io.github.ryanoviski.hestia.application.dto;

import java.time.Instant;
import java.util.List;

public record BackupManifest(int formatVersion,String applicationVersion,int schemaVersion,
                             Instant createdAt,long householdId,String databaseFilename,
                             List<BackupFile> attachments,List<BackupFile> files) {
    public record BackupFile(String path,long size,String sha256) { }
}
