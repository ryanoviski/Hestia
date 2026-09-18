package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.exceptions.ActiveProfileLimitException;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Profile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileServiceTest {
    @Test
    void rejectsBlankNameBeforePersistence() {
        InMemoryProfileRepository repository = new InMemoryProfileRepository();
        ProfileService service = new ProfileService(repository);

        assertThatThrownBy(() -> service.createProfile("  ", ProfileType.PERSON, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Informe o nome do perfil.");
        assertThat(repository.profiles).isEmpty();
    }

    @Test
    void preventsMoreThanFiveActiveProfiles() {
        InMemoryProfileRepository repository = new InMemoryProfileRepository();
        ProfileService service = new ProfileService(repository);
        for (int index = 1; index <= ProfileService.MAX_ACTIVE_PROFILES; index++) {
            service.createProfile("Perfil " + index, ProfileType.PERSON, null);
        }

        assertThatThrownBy(() -> service.createProfile("Perfil 6", ProfileType.SHARED, null))
                .isInstanceOf(ActiveProfileLimitException.class)
                .hasMessageContaining("cinco perfis ativos");
        assertThat(repository.profiles).hasSize(5);
    }

    @Test
    void rejectsInvalidColor() {
        ProfileService service = new ProfileService(new InMemoryProfileRepository());

        assertThatThrownBy(() -> service.createProfile("Ana", ProfileType.PERSON, "verde"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cor hexadecimal válida");
    }

    private static final class InMemoryProfileRepository implements ProfileRepository {
        private final List<Profile> profiles = new ArrayList<>();

        @Override public List<Profile> findAllByHousehold(long householdId) { return List.copyOf(profiles); }
        @Override public long countActiveByHousehold(long householdId) {
            return profiles.stream().filter(Profile::active).count();
        }
        @Override public Profile save(Profile profile) {
            Profile saved = new Profile((long) profiles.size() + 1, profile.householdId(), profile.name(),
                    profile.type(), profile.color(), profile.active(), profile.createdAt(), profile.updatedAt());
            profiles.add(saved);
            return saved;
        }
        @Override public void deactivate(long profileId, long householdId) { }
        @Override public long findDefaultHouseholdId() { return 1; }
        @Override public Optional<Profile> findById(long profileId) {
            return profiles.stream().filter(profile -> profile.id() == profileId).findFirst();
        }
    }
}
