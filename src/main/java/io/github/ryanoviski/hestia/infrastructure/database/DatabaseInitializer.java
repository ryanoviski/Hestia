package io.github.ryanoviski.hestia.infrastructure.database;

import io.github.ryanoviski.hestia.infrastructure.migrations.MigrationRunner;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class DatabaseInitializer {
    private static final List<String> INCOME_CATEGORIES = List.of(
            "Salário", "Renda extra", "Reembolso", "Outros"
    );
    private static final List<String> EXPENSE_CATEGORIES = List.of(
            "Moradia", "Alimentação", "Supermercado", "Saúde", "Farmácia", "Transporte",
            "Educação", "Lazer", "Assinaturas", "Compras", "Impostos", "Dívidas", "Outros"
    );

    private final ConnectionFactory connectionFactory;

    public DatabaseInitializer(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void initialize() {
        new MigrationRunner(connectionFactory).migrate();
        seedInitialData();
    }

    private void seedInitialData() {
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                seedHousehold(connection);
                seedCategories(connection, INCOME_CATEGORIES, "INCOME", "#3D8B7D");
                seedCategories(connection, EXPENSE_CATEGORIES, "EXPENSE", "#C76D5B");
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not create initial application data", exception);
        }
    }

    private void seedHousehold(Connection connection) throws SQLException {
        String now = Instant.now().toString();
        try (var statement = connection.prepareStatement("""
                INSERT INTO households(name, created_at, updated_at)
                SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM households)
                """)) {
            statement.setString(1, "Minha família");
            statement.setString(2, now);
            statement.setString(3, now);
            statement.executeUpdate();
        }
    }

    private void seedCategories(Connection connection, List<String> names, String type, String color)
            throws SQLException {
        String now = Instant.now().toString();
        try (var statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO categories
                    (household_id, name, category_type, color, icon, active, created_at, updated_at)
                VALUES (NULL, ?, ?, ?, NULL, 1, ?, ?)
                """)) {
            for (String name : names) {
                statement.setString(1, name);
                statement.setString(2, type);
                statement.setString(3, color);
                statement.setString(4, now);
                statement.setString(5, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
