package io.github.ryanoviski.hestia.domain.models;

import io.github.ryanoviski.hestia.domain.enums.ProfileType;

import java.time.Instant;

public record Profile(
        Long id,
        long householdId,
        String name,
        ProfileType type,
        String color,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static Profile newProfile(long householdId, String name, ProfileType type, String color) {
        Instant now = Instant.now();
        return new Profile(null, householdId, name, type, color, true, now, now);
    }
}
