package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
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
    void allowsMoreThanFiveActiveProfiles() {
        InMemoryProfileRepository repository = new InMemoryProfileRepository();
        ProfileService service = new ProfileService(repository);
        for (int index = 1; index <= 12; index++) {
            service.createProfile("Perfil " + index, ProfileType.PERSON, null);
        }

        assertThat(repository.profiles).hasSize(12).allMatch(Profile::active);
    }

    @Test
    void rejectsInvalidColor() {
        ProfileService service = new ProfileService(new InMemoryProfileRepository());

        assertThatThrownBy(() -> service.createProfile("Ana", ProfileType.PERSON, "verde"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cor válida");
    }

    @Test
    void updatesAndReactivatesProfile() {
        InMemoryProfileRepository repository = new InMemoryProfileRepository();
        ProfileService service = new ProfileService(repository);
        Profile profile = service.createProfile("Ana", ProfileType.PERSON, "#2F7F77");

        Profile updated = service.updateProfile(profile.id(), "Ana Maria", ProfileType.PERSON, "#3F6FA0");
        service.setActive(profile.id(), false);
        service.setActive(profile.id(), true);

        assertThat(updated.name()).isEqualTo("Ana Maria");
        assertThat(updated.color()).isEqualTo("#3F6FA0");
        assertThat(repository.findById(profile.id())).get().extracting(Profile::active).isEqualTo(true);
    }

    @Test
    void deletesOnlyUnreferencedProfile() {
        InMemoryProfileRepository repository = new InMemoryProfileRepository();
        ProfileService service = new ProfileService(repository);
        Profile profile = service.createProfile("Ana", ProfileType.PERSON, null);
        repository.referenced = true;

        assertThatThrownBy(() -> service.deleteProfile(profile.id()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Desative-o");

        repository.referenced = false;
        service.deleteProfile(profile.id());
        assertThat(repository.profiles).isEmpty();
    }

    private static final class InMemoryProfileRepository implements ProfileRepository {
        private final List<Profile> profiles = new ArrayList<>();
        private boolean referenced;

        @Override public List<Profile> findAllByHousehold(long householdId) { return List.copyOf(profiles); }
        @Override public Profile save(Profile profile) {
            Profile saved = new Profile((long) profiles.size() + 1, profile.householdId(), profile.name(),
                    profile.type(), profile.color(), profile.active(), profile.createdAt(), profile.updatedAt());
            profiles.add(saved);
            return saved;
        }
        @Override public Profile update(Profile profile) {
            profiles.replaceAll(current -> current.id().equals(profile.id()) ? profile : current);
            return profile;
        }
        @Override public void setActive(long profileId, long householdId, boolean active) {
            findById(profileId).ifPresent(profile -> update(new Profile(profile.id(), profile.householdId(),
                    profile.name(), profile.type(), profile.color(), active, profile.createdAt(), profile.updatedAt())));
        }
        @Override public boolean existsByNormalizedName(long householdId, String name, Long excludingId) {
            return profiles.stream().anyMatch(profile -> profile.householdId() == householdId
                    && (excludingId == null || !profile.id().equals(excludingId))
                    && profile.name().equalsIgnoreCase(name));
        }
        @Override public boolean isReferenced(long profileId) { return referenced; }
        @Override public void delete(long profileId, long householdId) {
            profiles.removeIf(profile -> profile.id() == profileId && profile.householdId() == householdId);
        }
        @Override public long findDefaultHouseholdId() { return 1; }
        @Override public Optional<Profile> findById(long profileId) {
            return profiles.stream().filter(profile -> profile.id() == profileId).findFirst();
        }
    }
}
