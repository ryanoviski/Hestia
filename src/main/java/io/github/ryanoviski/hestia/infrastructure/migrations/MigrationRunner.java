package io.github.ryanoviski.hestia.infrastructure.migrations;

import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MigrationRunner {
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "initial schema", "/db/migrations/V001__initial_schema.sql"),
            new Migration(2, "create transactions", "/db/migrations/V002__create_transactions.sql"),
            new Migration(3, "create financial commitments", "/db/migrations/V003__create_financial_commitments.sql"),
            new Migration(4, "create attachments", "/db/migrations/V004__create_attachments.sql"),
            new Migration(5, "create category preferences", "/db/migrations/V005__create_category_preferences.sql")
    );

    private final ConnectionFactory connectionFactory;

    public MigrationRunner(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void migrate() {
        try (Connection connection = connectionFactory.openConnection()) {
            createHistoryTable(connection);
            Set<Integer> appliedVersions = appliedVersions(connection);
            for (Migration migration : MIGRATIONS) {
                if (!appliedVersions.contains(migration.version())) {
                    apply(connection, migration);
                }
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not execute database migrations", exception);
        }
    }

    private void createHistoryTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_history (
                        version INTEGER PRIMARY KEY,
                        description TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private Set<Integer> appliedVersions(Connection connection) throws SQLException {
        Set<Integer> versions = new HashSet<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT version FROM schema_history")) {
            while (result.next()) {
                versions.add(result.getInt(1));
            }
        }
        return versions;
    }

    private void apply(Connection connection, Migration migration) {
        try {
            connection.setAutoCommit(false);
            for (String statementSql : readStatements(migration.resourcePath())) {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate(statementSql);
                }
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO schema_history(version, description, applied_at) VALUES (?, ?, ?)")) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.description());
                statement.setString(3, Instant.now().toString());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (Exception exception) {
            rollback(connection);
            throw new DatabaseException("Migration " + migration.version() + " failed: " + migration.description(), exception);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // The original database error is more useful to the caller.
            }
        }
    }

    private List<String> readStatements(String resourcePath) throws IOException {
        try (InputStream stream = MigrationRunner.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Migration resource not found: " + resourcePath);
            }
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder withoutComments = new StringBuilder();
            for (String line : sql.lines().toList()) {
                if (!line.stripLeading().startsWith("--")) {
                    withoutComments.append(line).append('\n');
                }
            }
            return List.of(withoutComments.toString().split(";"))
                    .stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
        }
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the migration failure as the primary exception.
        }
    }
}
