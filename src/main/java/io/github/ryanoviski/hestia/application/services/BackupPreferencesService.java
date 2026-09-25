package io.github.ryanoviski.hestia.application.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Properties;

public final class BackupPreferencesService {
    private static final int DEFAULT_RETENTION = 10;
    private static final int MAX_RETENTION = 100;

    public record Settings(boolean enabled, String directory, int retention,
                           Instant lastBackup, String lastResult) {
    }

    private final Path file;

    public BackupPreferencesService(Path dataDirectory) {
        file = dataDirectory.resolve("backup.properties");
    }

    public Settings load() {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            } catch (IOException ignored) {
                return defaults();
            }
        }
        return new Settings(
                Boolean.parseBoolean(properties.getProperty("enabled", "false")),
                properties.getProperty("directory", ""),
                retention(properties.getProperty("retention")),
                instant(properties.getProperty("lastBackup")),
                properties.getProperty("lastResult", "Nunca executado"));
    }

    public void save(Settings settings) {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(settings.enabled()));
        properties.setProperty("directory", settings.directory() == null ? "" : settings.directory());
        properties.setProperty("retention", Integer.toString(clampRetention(settings.retention())));
        if (settings.lastBackup() != null) {
            properties.setProperty("lastBackup", settings.lastBackup().toString());
        }
        properties.setProperty("lastResult", settings.lastResult() == null ? "" : settings.lastResult());

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Hestia backup settings - no passwords are stored");
            }
            moveReplacing(temporary, file);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Keep the original write failure as the primary error.
            }
            throw new IllegalStateException("Could not save backup settings", exception);
        }
    }

    private Settings defaults() {
        return new Settings(false, "", DEFAULT_RETENTION, null, "Nunca executado");
    }

    private int retention(String value) {
        try {
            return clampRetention(value == null ? DEFAULT_RETENTION : Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return DEFAULT_RETENTION;
        }
    }

    private int clampRetention(int value) {
        return Math.max(1, Math.min(MAX_RETENTION, value));
    }

    private Instant instant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path file() {
        return file;
    }
}
