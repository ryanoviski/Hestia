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
import java.util.Optional;

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
    public Profile update(Profile profile) {
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement("""
                     UPDATE profiles SET name = ?, profile_type = ?, color = ?, updated_at = ?
                     WHERE id = ? AND household_id = ?
                     """)) {
            statement.setString(1, profile.name());
            statement.setString(2, profile.type().name());
            statement.setString(3, profile.color());
            statement.setString(4, profile.updatedAt().toString());
            statement.setLong(5, profile.id());
            statement.setLong(6, profile.householdId());
            if (statement.executeUpdate() != 1) throw new SQLException("Profile was not updated");
            return profile;
        } catch (SQLException exception) {
            throw new DatabaseException("Could not update profile", exception);
        }
    }

    @Override
    public void setActive(long profileId, long householdId, boolean active) {
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement("""
                     UPDATE profiles SET active = ?, updated_at = ?
                     WHERE id = ? AND household_id = ?
                     """)) {
            statement.setBoolean(1, active);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, profileId);
            statement.setLong(4, householdId);
            if (statement.executeUpdate() != 1) throw new SQLException("Profile status was not updated");
        } catch (SQLException exception) {
            throw new DatabaseException("Could not change profile status", exception);
        }
    }

    @Override
    public boolean existsByNormalizedName(long householdId, String name, Long excludingId) {
        String sql = """
                SELECT 1 FROM profiles
                WHERE household_id = ? AND lower(trim(name)) = lower(trim(?))
                  AND (? IS NULL OR id != ?) LIMIT 1
                """;
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, householdId);
            statement.setString(2, name);
            if (excludingId == null) statement.setNull(3, java.sql.Types.INTEGER); else statement.setLong(3, excludingId);
            if (excludingId == null) statement.setNull(4, java.sql.Types.INTEGER); else statement.setLong(4, excludingId);
            try (var result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not check profile name", exception);
        }
    }

    @Override
    public boolean isReferenced(long profileId) {
        String sql = """
                SELECT 1 FROM transactions WHERE profile_id = ?
                UNION ALL SELECT 1 FROM recurring_expenses WHERE profile_id = ?
                UNION ALL SELECT 1 FROM installment_plans WHERE profile_id = ?
                UNION ALL SELECT 1 FROM attachments WHERE profile_id = ?
                LIMIT 1
                """;
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= 4; index++) statement.setLong(index, profileId);
            try (var result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not check profile references", exception);
        }
    }

    @Override
    public void delete(long profileId, long householdId) {
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM profiles WHERE id = ? AND household_id = ?")) {
            statement.setLong(1, profileId);
            statement.setLong(2, householdId);
            if (statement.executeUpdate() != 1) throw new SQLException("Profile was not deleted");
        } catch (SQLException exception) {
            throw new DatabaseException("Could not delete profile", exception);
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

    @Override
    public Optional<Profile> findById(long profileId) {
        try (var connection = connectionFactory.openConnection();
             var statement = connection.prepareStatement("SELECT * FROM profiles WHERE id = ?")) {
            statement.setLong(1, profileId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Could not find profile", exception);
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
