package io.github.ryanoviski.hestia.application.repositories;

import io.github.ryanoviski.hestia.domain.models.Profile;

import java.util.List;
import java.util.Optional;

public interface ProfileRepository {
    List<Profile> findAllByHousehold(long householdId);

    long countActiveByHousehold(long householdId);

    Profile save(Profile profile);

    Profile update(Profile profile);

    void setActive(long profileId, long householdId, boolean active);

    boolean existsByNormalizedName(long householdId, String name, Long excludingId);

    boolean isReferenced(long profileId);

    void delete(long profileId, long householdId);

    long findDefaultHouseholdId();

    Optional<Profile> findById(long profileId);
}
