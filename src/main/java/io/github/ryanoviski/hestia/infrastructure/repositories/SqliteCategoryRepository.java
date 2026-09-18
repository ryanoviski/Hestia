package io.github.ryanoviski.hestia.infrastructure.repositories;

import io.github.ryanoviski.hestia.application.repositories.CategoryRepository;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteCategoryRepository implements CategoryRepository {
    private final ConnectionFactory connections;

    public SqliteCategoryRepository(ConnectionFactory connections) { this.connections = connections; }

    @Override
    public List<Category> search(long householdId, CategoryType type, String name, boolean includeInactive) {
        StringBuilder sql = new StringBuilder("SELECT * FROM categories WHERE (household_id IS NULL OR household_id = ?)");
        if (type != null) sql.append(" AND category_type = ?");
        if (name != null && !name.isBlank()) sql.append(" AND lower(name) LIKE lower(?)");
        if (!includeInactive) sql.append(" AND active = 1");
        sql.append(" ORDER BY category_type, active DESC, name COLLATE NOCASE");
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setLong(index++, householdId);
            if (type != null) statement.setString(index++, type.name());
            if (name != null && !name.isBlank()) statement.setString(index, "%" + name.trim() + "%");
            try (var result = statement.executeQuery()) {
                List<Category> categories = new ArrayList<>();
                while (result.next()) categories.add(map(result));
                return categories;
            }
        } catch (SQLException exception) { throw failure("search categories", exception); }
    }

    @Override
    public Optional<Category> findById(long id) {
        try (var connection = connections.openConnection();
             var statement = connection.prepareStatement("SELECT * FROM categories WHERE id = ?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) { throw failure("find category", exception); }
    }

    @Override
    public boolean existsByNormalizedName(long householdId, CategoryType type, String name, Long excludingId) {
        String sql = """
                SELECT 1 FROM categories
                WHERE (household_id IS NULL OR household_id = ?) AND category_type = ?
                  AND lower(trim(name)) = lower(trim(?)) AND (? IS NULL OR id != ?) LIMIT 1
                """;
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, householdId);
            statement.setString(2, type.name());
            statement.setString(3, name);
            if (excludingId == null) statement.setNull(4, java.sql.Types.INTEGER); else statement.setLong(4, excludingId);
            if (excludingId == null) statement.setNull(5, java.sql.Types.INTEGER); else statement.setLong(5, excludingId);
            try (var result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException exception) { throw failure("check category name", exception); }
    }

    @Override
    public Category insert(Category category) {
        String sql = """
                INSERT INTO categories(household_id,name,category_type,color,icon,active,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?)
                """;
        try (var connection = connections.openConnection();
             var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, category);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No category identifier returned");
                return new Category(keys.getLong(1), category.householdId(), category.name(), category.type(),
                        category.color(), category.icon(), category.active(), category.createdAt(), category.updatedAt());
            }
        } catch (SQLException exception) { throw failure("insert category", exception); }
    }

    @Override
    public Category update(Category category) {
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement("""
                UPDATE categories SET name=?, category_type=?, color=?, updated_at=?
                WHERE id=? AND household_id=?
                """)) {
            statement.setString(1, category.name());
            statement.setString(2, category.type().name());
            statement.setString(3, category.color());
            statement.setString(4, category.updatedAt().toString());
            statement.setLong(5, category.id());
            statement.setLong(6, category.householdId());
            if (statement.executeUpdate() != 1) throw new SQLException("Category was not updated");
            return category;
        } catch (SQLException exception) { throw failure("update category", exception); }
    }

    @Override
    public void setActive(long categoryId, long householdId, boolean active) {
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement(
                "UPDATE categories SET active=?, updated_at=? WHERE id=? AND household_id=?")) {
            statement.setBoolean(1, active);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, categoryId);
            statement.setLong(4, householdId);
            if (statement.executeUpdate() != 1) throw new SQLException("Category was not updated");
        } catch (SQLException exception) { throw failure("change category status", exception); }
    }

    @Override
    public boolean isReferenced(long categoryId) {
        try (var connection = connections.openConnection();
             var statement = connection.prepareStatement("SELECT 1 FROM transactions WHERE category_id=? LIMIT 1")) {
            statement.setLong(1, categoryId);
            try (var result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException exception) { throw failure("check category references", exception); }
    }

    private void bind(java.sql.PreparedStatement statement, Category category) throws SQLException {
        statement.setLong(1, category.householdId());
        statement.setString(2, category.name());
        statement.setString(3, category.type().name());
        statement.setString(4, category.color());
        statement.setString(5, category.icon());
        statement.setBoolean(6, category.active());
        statement.setString(7, category.createdAt().toString());
        statement.setString(8, category.updatedAt().toString());
    }

    private Category map(ResultSet result) throws SQLException {
        long household = result.getLong("household_id");
        Long nullableHousehold = result.wasNull() ? null : household;
        return new Category(result.getLong("id"), nullableHousehold, result.getString("name"),
                CategoryType.valueOf(result.getString("category_type")), result.getString("color"),
                result.getString("icon"), result.getBoolean("active"),
                Instant.parse(result.getString("created_at")), Instant.parse(result.getString("updated_at")));
    }

    private DatabaseException failure(String action, SQLException exception) {
        return new DatabaseException("Could not " + action, exception);
    }
}
