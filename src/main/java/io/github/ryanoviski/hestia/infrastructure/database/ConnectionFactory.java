package io.github.ryanoviski.hestia.infrastructure.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class ConnectionFactory {
    private final String jdbcUrl;

    public ConnectionFactory(Path databaseFile) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath().normalize();
    }

    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }
}
