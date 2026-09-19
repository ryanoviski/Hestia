package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.exceptions.ActiveProfileLimitException;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Profile;

import java.util.List;
import java.util.Objects;
import java.time.Instant;

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
        String normalizedName = validateNameAndType(name, type);
        long householdId = repository.findDefaultHouseholdId();
        if (repository.countActiveByHousehold(householdId) >= MAX_ACTIVE_PROFILES) {
            throw new ActiveProfileLimitException();
        }
        ensureUnique(householdId, normalizedName, null);
        String normalizedColor = normalizeColor(color);
        return repository.save(Profile.newProfile(householdId, normalizedName, type, normalizedColor));
    }

    public Profile updateProfile(long profileId, String name, ProfileType type, String color) {
        long householdId = repository.findDefaultHouseholdId();
        Profile existing = ownedProfile(profileId, householdId);
        String normalizedName = validateNameAndType(name, type);
        ensureUnique(householdId, normalizedName, profileId);
        if (existing.type() != type && repository.isReferenced(profileId)) {
            throw new ValidationException("O tipo de um perfil em uso não pode ser alterado. O histórico foi preservado.");
        }
        return repository.update(new Profile(existing.id(), householdId, normalizedName, type,
                normalizeColor(color), existing.active(), existing.createdAt(), Instant.now()));
    }

    public void deactivateProfile(long profileId) {
        setActive(profileId, false);
    }

    public void setActive(long profileId, boolean active) {
        long householdId = repository.findDefaultHouseholdId();
        Profile profile = ownedProfile(profileId, householdId);
        if (active && !profile.active() && repository.countActiveByHousehold(householdId) >= MAX_ACTIVE_PROFILES) {
            throw new ActiveProfileLimitException();
        }
        repository.setActive(profileId, householdId, active);
    }

    public void deleteProfile(long profileId) {
        long householdId = repository.findDefaultHouseholdId();
        ownedProfile(profileId, householdId);
        if (repository.isReferenced(profileId)) {
            throw new ValidationException("Este perfil possui movimentações, compromissos ou anexos. Desative-o para preservar o histórico.");
        }
        repository.delete(profileId, householdId);
    }

    private Profile ownedProfile(long profileId, long householdId) {
        Profile profile = repository.findById(profileId)
                .orElseThrow(() -> new ValidationException("Perfil não encontrado."));
        if (profile.householdId() != householdId) {
            throw new ValidationException("Perfil inválido para este grupo.");
        }
        return profile;
    }

    private String validateNameAndType(String name, ProfileType type) {
        String normalizedName = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (normalizedName.isEmpty()) throw new ValidationException("Informe o nome do perfil.");
        if (normalizedName.length() > 80)
            throw new ValidationException("O nome do perfil deve ter no máximo 80 caracteres.");
        if (type == null) throw new ValidationException("Selecione o tipo do perfil.");
        return normalizedName;
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) return null;
        String normalized = color.trim().toUpperCase();
        if (!normalized.matches("#[0-9A-F]{6}"))
            throw new ValidationException("Selecione uma cor válida para o perfil.");
        return normalized;
    }

    private void ensureUnique(long householdId, String name, Long excludingId) {
        if (repository.existsByNormalizedName(householdId, name, excludingId)) {
            throw new ValidationException("Já existe um perfil com esse nome.");
        }
    }
}
