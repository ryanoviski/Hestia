package io.github.ryanoviski.hestia.infrastructure.repositories;

import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteProfileRepositoryTest {
    @TempDir Path temporaryDirectory;
    private SqliteProfileRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("isolated").resolve("hestia.db");
        Files.createDirectories(database.getParent());
        ConnectionFactory factory = new ConnectionFactory(database);
        new DatabaseInitializer(factory).initialize();
        repository = new SqliteProfileRepository(factory);
    }

    @Test
    void persistsAndReadsProfile() {
        long householdId = repository.findDefaultHouseholdId();
        Profile saved = repository.save(Profile.newProfile(
                householdId, "Família", ProfileType.SHARED, "#3D8B7D"));

        assertThat(saved.id()).isPositive();
        assertThat(repository.findAllByHousehold(householdId))
                .singleElement()
                .satisfies(profile -> {
                    assertThat(profile.name()).isEqualTo("Família");
                    assertThat(profile.type()).isEqualTo(ProfileType.SHARED);
                    assertThat(profile.color()).isEqualTo("#3D8B7D");
                    assertThat(profile.active()).isTrue();
                });
    }
}
