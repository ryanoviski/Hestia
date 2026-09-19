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
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.household_id, COALESCE(p.name, c.name) AS name,
                       c.category_type, COALESCE(p.color, c.color) AS color, c.icon,
                       COALESCE(p.active, c.active) AS active, c.created_at,
                       COALESCE(p.updated_at, c.updated_at) AS updated_at
                FROM categories c
                LEFT JOIN category_preferences p ON p.category_id = c.id AND p.household_id = ?
                WHERE (c.household_id IS NULL OR c.household_id = ?)
                """);
        if (type != null) sql.append(" AND c.category_type = ?");
        if (name != null && !name.isBlank()) sql.append(" AND lower(COALESCE(p.name, c.name)) LIKE lower(?)");
        if (!includeInactive) sql.append(" AND COALESCE(p.active, c.active) = 1");
        sql.append(" ORDER BY c.category_type, COALESCE(p.active, c.active) DESC, COALESCE(p.name, c.name) COLLATE NOCASE");
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setLong(index++, householdId);
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
    public Optional<Category> findById(long id, long householdId) {
        try (var connection = connections.openConnection();
             var statement = connection.prepareStatement("""
                     SELECT c.id, c.household_id, COALESCE(p.name, c.name) AS name,
                            c.category_type, COALESCE(p.color, c.color) AS color, c.icon,
                            COALESCE(p.active, c.active) AS active, c.created_at,
                            COALESCE(p.updated_at, c.updated_at) AS updated_at
                     FROM categories c
                     LEFT JOIN category_preferences p ON p.category_id = c.id AND p.household_id = ?
                     WHERE c.id = ? AND (c.household_id IS NULL OR c.household_id = ?)
                     """)) {
            statement.setLong(1, householdId);
            statement.setLong(2, id);
            statement.setLong(3, householdId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) { throw failure("find category", exception); }
    }

    @Override
    public boolean existsByNormalizedName(long householdId, CategoryType type, String name, Long excludingId) {
        String sql = """
                SELECT 1 FROM categories c
                LEFT JOIN category_preferences p ON p.category_id = c.id AND p.household_id = ?
                WHERE (c.household_id IS NULL OR c.household_id = ?) AND c.category_type = ?
                  AND lower(trim(COALESCE(p.name, c.name))) = lower(trim(?))
                  AND (? IS NULL OR c.id != ?) LIMIT 1
                """;
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, householdId);
            statement.setLong(2, householdId);
            statement.setString(3, type.name());
            statement.setString(4, name);
            if (excludingId == null) statement.setNull(5, java.sql.Types.INTEGER); else statement.setLong(5, excludingId);
            if (excludingId == null) statement.setNull(6, java.sql.Types.INTEGER); else statement.setLong(6, excludingId);
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
    public Category updateStandardPreference(long householdId, Category category) {
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement("""
                INSERT INTO category_preferences(household_id, category_id, name, color, active, updated_at)
                VALUES(?,?,?,?,?,?)
                ON CONFLICT(household_id, category_id) DO UPDATE SET
                    name=excluded.name, color=excluded.color, active=excluded.active, updated_at=excluded.updated_at
                """)) {
            statement.setLong(1, householdId);
            statement.setLong(2, category.id());
            statement.setString(3, category.name());
            statement.setString(4, category.color());
            statement.setBoolean(5, category.active());
            statement.setString(6, category.updatedAt().toString());
            statement.executeUpdate();
            return category;
        } catch (SQLException exception) { throw failure("update standard category preference", exception); }
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
    public void setStandardActive(long categoryId, long householdId, boolean active) {
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement("""
                INSERT INTO category_preferences(household_id, category_id, active, updated_at)
                VALUES(?,?,?,?)
                ON CONFLICT(household_id, category_id) DO UPDATE SET
                    active=excluded.active, updated_at=excluded.updated_at
                """)) {
            statement.setLong(1, householdId);
            statement.setLong(2, categoryId);
            statement.setBoolean(3, active);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) { throw failure("change standard category visibility", exception); }
    }

    @Override
    public boolean isReferenced(long categoryId) {
        try (var connection = connections.openConnection();
             var statement = connection.prepareStatement("""
                     SELECT 1 FROM transactions WHERE category_id = ?
                     UNION ALL SELECT 1 FROM recurring_expenses WHERE category_id = ?
                     UNION ALL SELECT 1 FROM installment_plans WHERE category_id = ?
                     LIMIT 1
                     """)) {
            statement.setLong(1, categoryId);
            statement.setLong(2, categoryId);
            statement.setLong(3, categoryId);
            try (var result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException exception) { throw failure("check category references", exception); }
    }

    @Override
    public void delete(long categoryId, long householdId) {
        try (var connection = connections.openConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM categories WHERE id=? AND household_id=?")) {
            statement.setLong(1, categoryId);
            statement.setLong(2, householdId);
            if (statement.executeUpdate() != 1) throw new SQLException("Category was not deleted");
        } catch (SQLException exception) { throw failure("delete category", exception); }
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
