package io.github.ryanoviski.hestia.application.dto;

public record ValidatedFile(String extension,String mediaType,long sizeBytes,String sha256) { }
