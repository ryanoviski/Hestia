package io.github.ryanoviski.hestia.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseInitializerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void createsDatabaseAndRunsMigrationOnlyOnce() throws Exception {
        Path database = temporaryDirectory.resolve("data").resolve("test.db");
        Files.createDirectories(database.getParent());
        ConnectionFactory factory = new ConnectionFactory(database);
        DatabaseInitializer initializer = new DatabaseInitializer(factory);

        initializer.initialize();
        initializer.initialize();

        assertThat(database).exists();
        try (var connection = factory.openConnection();
             var history = connection.createStatement().executeQuery("SELECT COUNT(*) FROM schema_history")) {
            assertThat(history.getInt(1)).isEqualTo(3);
        }
        try (var connection = factory.openConnection();
             var households = connection.createStatement().executeQuery("SELECT COUNT(*) FROM households")) {
            assertThat(households.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void seedsExpectedCategoriesWithoutDuplicates() throws Exception {
        ConnectionFactory factory = initializedFactory();
        new DatabaseInitializer(factory).initialize();

        try (var connection = factory.openConnection();
             var result = connection.createStatement().executeQuery("""
                     SELECT category_type, COUNT(*) AS total
                     FROM categories GROUP BY category_type ORDER BY category_type
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("category_type")).isEqualTo("EXPENSE");
            assertThat(result.getInt("total")).isEqualTo(13);
            assertThat(result.next()).isTrue();
            assertThat(result.getString("category_type")).isEqualTo("INCOME");
            assertThat(result.getInt("total")).isEqualTo(4);
            assertThat(result.next()).isFalse();
        }
    }

    @Test
    void enforcesForeignKeysOnEveryConnection() throws Exception {
        ConnectionFactory factory = initializedFactory();

        assertThatThrownBy(() -> insertProfileWithUnknownHousehold(factory))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("FOREIGN KEY");
    }

    private ConnectionFactory initializedFactory() throws Exception {
        Path database = temporaryDirectory.resolve("db").resolve("hestia.db");
        Files.createDirectories(database.getParent());
        ConnectionFactory factory = new ConnectionFactory(database);
        new DatabaseInitializer(factory).initialize();
        return factory;
    }

    private void insertProfileWithUnknownHousehold(ConnectionFactory factory) throws SQLException {
        try (var connection = factory.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO profiles
                     (household_id, name, profile_type, active, created_at, updated_at)
                     VALUES (99999, 'Teste', 'PERSON', 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                     """)) {
            statement.executeUpdate();
        }
    }
}
