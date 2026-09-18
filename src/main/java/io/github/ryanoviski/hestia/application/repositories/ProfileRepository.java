package io.github.ryanoviski.hestia.application.repositories;

import io.github.ryanoviski.hestia.domain.models.Profile;

import java.util.List;

public interface ProfileRepository {
    List<Profile> findAllByHousehold(long householdId);

    long countActiveByHousehold(long householdId);

    Profile save(Profile profile);

    void deactivate(long profileId, long householdId);

    long findDefaultHouseholdId();
}
