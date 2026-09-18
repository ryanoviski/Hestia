package io.github.ryanoviski.hestia.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ApplicationPaths {
    public static final String DATA_DIRECTORY_PROPERTY = "hestia.data.dir";
    public static final String DATA_DIRECTORY_ENVIRONMENT = "HESTIA_DATA_DIR";

    private final Path root;

    private ApplicationPaths(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public static ApplicationPaths resolve() {
        String property = System.getProperty(DATA_DIRECTORY_PROPERTY);
        if (property != null && !property.isBlank()) {
            return new ApplicationPaths(Path.of(property));
        }
        String environment = System.getenv(DATA_DIRECTORY_ENVIRONMENT);
        if (environment != null && !environment.isBlank()) {
            return new ApplicationPaths(Path.of(environment));
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local")
                : Path.of(localAppData);
        return new ApplicationPaths(base.resolve("Hestia"));
    }

    public static ApplicationPaths at(Path root) {
        return new ApplicationPaths(Objects.requireNonNull(root, "root"));
    }

    public void createDirectories() throws IOException {
        Files.createDirectories(dataDirectory());
        Files.createDirectories(attachmentsDirectory());
        Files.createDirectories(backupsDirectory());
        Files.createDirectories(cacheDirectory());
        Files.createDirectories(temporaryDirectory());
        Files.createDirectories(logsDirectory());
    }

    public Path root() { return root; }
    public Path dataDirectory() { return root.resolve("data"); }
    public Path databaseFile() { return dataDirectory().resolve("hestia.db"); }
    public Path attachmentsDirectory() { return root.resolve("attachments"); }
    public Path backupsDirectory() { return root.resolve("backups"); }
    public Path cacheDirectory() { return root.resolve("cache"); }
    public Path temporaryDirectory() { return root.resolve("temp"); }
    public Path logsDirectory() { return root.resolve("logs"); }
}
