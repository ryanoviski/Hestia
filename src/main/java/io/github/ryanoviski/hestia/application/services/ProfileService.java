package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.exceptions.ActiveProfileLimitException;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Profile;

import java.util.List;
import java.util.Objects;

public final class ProfileService {
    public static final int MAX_ACTIVE_PROFILES = 5;

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<Profile> listProfiles() {
        return repository.findAllByHousehold(repository.findDefaultHouseholdId());
    }

    public Profile createProfile(String name, ProfileType type, String color) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            throw new ValidationException("Informe o nome do perfil.");
        }
        if (normalizedName.length() > 80) {
            throw new ValidationException("O nome do perfil deve ter no máximo 80 caracteres.");
        }
        if (type == null) {
            throw new ValidationException("Selecione o tipo do perfil.");
        }

        long householdId = repository.findDefaultHouseholdId();
        if (repository.countActiveByHousehold(householdId) >= MAX_ACTIVE_PROFILES) {
            throw new ActiveProfileLimitException();
        }
        String normalizedColor = color == null || color.isBlank() ? null : color.trim();
        if (normalizedColor != null && !normalizedColor.matches("#[0-9a-fA-F]{6}")) {
            throw new ValidationException("Informe uma cor hexadecimal válida, como #3D8B7D.");
        }
        return repository.save(Profile.newProfile(householdId, normalizedName, type, normalizedColor));
    }

    public void deactivateProfile(long profileId) {
        repository.deactivate(profileId, repository.findDefaultHouseholdId());
    }
}
