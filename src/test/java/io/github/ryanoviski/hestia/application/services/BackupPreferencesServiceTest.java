package io.github.ryanoviski.hestia.application.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BackupPreferencesServiceTest {
    @TempDir Path directory;

    @Test
    void corruptedOptionalValuesFallBackWithoutPreventingStartup() throws Exception {
        BackupPreferencesService service = new BackupPreferencesService(directory);
        Files.writeString(service.file(), """
                enabled=true
                directory=C:\\\\Backups
                retention=not-a-number
                lastBackup=invalid-date
                lastResult=Interrompido
                """);

        BackupPreferencesService.Settings settings = service.load();

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.directory()).isEqualTo("C:\\Backups");
        assertThat(settings.retention()).isEqualTo(10);
        assertThat(settings.lastBackup()).isNull();
        assertThat(settings.lastResult()).isEqualTo("Interrompido");
    }

    @Test
    void savesAtomicallyAndClampsRetentionToSupportedRange() {
        BackupPreferencesService service = new BackupPreferencesService(directory);
        Instant instant = Instant.parse("2026-09-24T10:00:00Z");

        service.save(new BackupPreferencesService.Settings(true, "C:\\Backups", 500,
                instant, "Concluído"));

        assertThat(service.load()).isEqualTo(new BackupPreferencesService.Settings(
                true, "C:\\Backups", 100, instant, "Concluído"));
        assertThat(directory.resolve("backup.properties.tmp")).doesNotExist();
    }
}
