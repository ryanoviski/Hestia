package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.repositories.CategoryRepository;
import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;

final class CommitmentValidator {
    private CommitmentValidator() { }
    static void validateReferences(long householdId, long profileId, long categoryId,
                                   ProfileRepository profiles, CategoryRepository categories) {
        var profile = profiles.findById(profileId)
                .orElseThrow(() -> new ValidationException("Selecione um perfil válido."));
        if (profile.householdId() != householdId || !profile.active())
            throw new ValidationException("O perfil selecionado está inativo ou não pertence ao grupo.");
        var category = categories.findById(categoryId, householdId)
                .orElseThrow(() -> new ValidationException("Selecione uma categoria válida."));
        if ((category.householdId() != null && category.householdId() != householdId)
                || !category.active() || category.type() != CategoryType.EXPENSE)
            throw new ValidationException("Selecione uma categoria de despesa ativa.");
    }
    static String description(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new ValidationException("Informe a descrição.");
        if (result.length() > 150) throw new ValidationException("A descrição deve ter no máximo 150 caracteres.");
        return result;
    }
    static String notes(String value) {
        String result = value == null || value.isBlank() ? null : value.trim();
        if (result != null && result.length() > 1000)
            throw new ValidationException("As observações devem ter no máximo 1.000 caracteres.");
        return result;
    }
}
