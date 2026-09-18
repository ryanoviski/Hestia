package io.github.ryanoviski.hestia.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPathsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void createsCompleteDirectoryLayoutAtConfigurableLocation() throws Exception {
        ApplicationPaths paths = ApplicationPaths.at(temporaryDirectory.resolve("custom-hestia"));
        paths.createDirectories();

        assertThat(paths.dataDirectory()).isDirectory();
        assertThat(paths.attachmentsDirectory()).isDirectory();
        assertThat(paths.backupsDirectory()).isDirectory();
        assertThat(paths.logsDirectory()).isDirectory();
        assertThat(paths.databaseFile()).hasFileName("hestia.db");
    }
}
