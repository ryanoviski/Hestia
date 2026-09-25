package io.github.ryanoviski.hestia.infrastructure.database;

import io.github.ryanoviski.hestia.infrastructure.migrations.MigrationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationCompatibilityTest {
    @TempDir Path directory;

    @Test
    void refusesDatabaseCreatedByANewerApplicationVersion() throws Exception {
        Path database = directory.resolve("data/hestia.db");
        Files.createDirectories(database.getParent());
        ConnectionFactory factory = new ConnectionFactory(database);
        try (var connection = factory.openConnection()) {
            connection.createStatement().execute("""
                    CREATE TABLE schema_history (
                        version INTEGER PRIMARY KEY,
                        description TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    INSERT INTO schema_history(version, description, applied_at)
                    VALUES (999, 'future migration', '2030-01-01T00:00:00Z')
                    """);
        }

        assertThatThrownBy(() -> new MigrationRunner(factory).migrate())
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("unsupported migration versions")
                .hasMessageContaining("999");
    }
}
