package io.github.ryanoviski.hestia.application.repositories;

import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.models.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    List<Category> search(long householdId, CategoryType type, String name, boolean includeInactive);
    Optional<Category> findById(long id, long householdId);
    boolean existsByNormalizedName(long householdId, CategoryType type, String name, Long excludingId);
    Category insert(Category category);
    Category update(Category category);
    Category updateStandardPreference(long householdId, Category category);
    void setActive(long categoryId, long householdId, boolean active);
    void setStandardActive(long categoryId, long householdId, boolean active);
    boolean isReferenced(long categoryId);
    void delete(long categoryId, long householdId);
}
