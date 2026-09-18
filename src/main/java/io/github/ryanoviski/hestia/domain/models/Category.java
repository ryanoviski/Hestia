package io.github.ryanoviski.hestia.domain.models;

import io.github.ryanoviski.hestia.domain.enums.CategoryType;

import java.time.Instant;

public record Category(Long id, Long householdId, String name, CategoryType type, String color,
                       String icon, boolean active, Instant createdAt, Instant updatedAt) {
    public boolean standard() { return householdId == null; }
    @Override public String toString() { return name; }
}
