package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.repositories.CategoryRepository;
import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;

import java.time.Instant;
import java.util.List;

public final class CategoryService {
    private final CategoryRepository repository;
    private final ProfileRepository profiles;

    public CategoryService(CategoryRepository repository, ProfileRepository profiles) {
        this.repository = repository;
        this.profiles = profiles;
    }

    public List<Category> search(CategoryType type, String name, boolean includeInactive) {
        return repository.search(profiles.findDefaultHouseholdId(), type, name, includeInactive);
    }

    public Category create(String name, CategoryType type, String color) {
        long householdId = profiles.findDefaultHouseholdId();
        String normalized = validate(name, type, color);
        ensureUnique(householdId, type, normalized, null);
        Instant now = Instant.now();
        return repository.insert(new Category(null, householdId, normalized, type, normalizeColor(color),
                null, true, now, now));
    }

    public Category update(long id, String name, CategoryType type, String color) {
        long householdId = profiles.findDefaultHouseholdId();
        Category existing = ownedCategory(id, householdId);
        String normalized = validate(name, type, color);
        if (existing.type() != type && repository.isReferenced(id)) {
            throw new ValidationException("O tipo de uma categoria em uso não pode ser alterado.");
        }
        ensureUnique(householdId, type, normalized, id);
        return repository.update(new Category(id, householdId, normalized, type, normalizeColor(color),
                existing.icon(), existing.active(), existing.createdAt(), Instant.now()));
    }

    public void setActive(long id, boolean active) {
        long householdId = profiles.findDefaultHouseholdId();
        ownedCategory(id, householdId);
        repository.setActive(id, householdId, active);
    }

    private Category ownedCategory(long id, long householdId) {
        Category category = repository.findById(id)
                .orElseThrow(() -> new ValidationException("Categoria não encontrada."));
        if (category.standard()) throw new ValidationException("Categorias padrão não podem ser alteradas.");
        if (!category.householdId().equals(householdId)) throw new ValidationException("Categoria inválida para este grupo.");
        return category;
    }

    private String validate(String name, CategoryType type, String color) {
        String normalized = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) throw new ValidationException("Informe o nome da categoria.");
        if (normalized.length() > 80) throw new ValidationException("O nome deve ter no máximo 80 caracteres.");
        if (type == null) throw new ValidationException("Selecione o tipo da categoria.");
        normalizeColor(color);
        return normalized;
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) return null;
        String normalized = color.trim();
        if (!normalized.matches("#[0-9a-fA-F]{6}"))
            throw new ValidationException("Informe uma cor hexadecimal válida, como #3D8B7D.");
        return normalized.toUpperCase();
    }

    private void ensureUnique(long householdId, CategoryType type, String name, Long excludingId) {
        if (repository.existsByNormalizedName(householdId, type, name, excludingId))
            throw new ValidationException("Já existe uma categoria com esse nome e tipo.");
    }
}
