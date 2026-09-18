package io.github.ryanoviski.hestia.infrastructure.repositories;

import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SqliteProfileRepository implements ProfileRepository {
    private final ConnectionFactory connectionFactory;

    public SqliteProfileRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public List<Profile> findAllByHousehold(long householdId) {
        String sql = "SELECT * FROM profiles WHERE household_id = ? ORDER BY active DESC, name COLLATE NOCASE";
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, householdId);
            try (var result = statement.executeQuery()) {
                List<Profile> profiles = new ArrayList<>();
                while (result.next()) {
                    profiles.add(map(result));
                }
                return profiles;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not read profiles", exception);
        }
    }

    @Override
    public long countActiveByHousehold(long householdId) {
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM profiles WHERE household_id = ? AND active = 1")) {
            statement.setLong(1, householdId);
            try (var result = statement.executeQuery()) {
                return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not count active profiles", exception);
        }
    }

    @Override
    public Profile save(Profile profile) {
        String sql = """
                INSERT INTO profiles(household_id, name, profile_type, color, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, profile.householdId());
            statement.setString(2, profile.name());
            statement.setString(3, profile.type().name());
            statement.setString(4, profile.color());
            statement.setBoolean(5, profile.active());
            statement.setString(6, profile.createdAt().toString());
            statement.setString(7, profile.updatedAt().toString());
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No identifier returned for the new profile");
                }
                return new Profile(keys.getLong(1), profile.householdId(), profile.name(), profile.type(),
                        profile.color(), profile.active(), profile.createdAt(), profile.updatedAt());
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not save profile", exception);
        }
    }

    @Override
    public void deactivate(long profileId, long householdId) {
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement("""
                     UPDATE profiles SET active = 0, updated_at = ?
                     WHERE id = ? AND household_id = ? AND active = 1
                     """)) {
            statement.setString(1, Instant.now().toString());
            statement.setLong(2, profileId);
            statement.setLong(3, householdId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Could not deactivate profile", exception);
        }
    }

    @Override
    public long findDefaultHouseholdId() {
        try (var connection = connectionFactory.openConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT id FROM households ORDER BY id LIMIT 1")) {
            if (!result.next()) {
                throw new SQLException("Default household was not initialized");
            }
            return result.getLong(1);
        } catch (SQLException exception) {
            throw new DatabaseException("Could not find the default household", exception);
        }
    }

    private Profile map(ResultSet result) throws SQLException {
        return new Profile(
                result.getLong("id"),
                result.getLong("household_id"),
                result.getString("name"),
                ProfileType.valueOf(result.getString("profile_type")),
                result.getString("color"),
                result.getBoolean("active"),
                Instant.parse(result.getString("created_at")),
                Instant.parse(result.getString("updated_at"))
        );
    }
}
