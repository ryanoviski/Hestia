package io.github.ryanoviski.hestia.application.dto;

import java.util.List;
public record StorageDiagnostic(List<Long> missingRecords,List<String> orphanFiles,List<String> temporaryFiles) { }
