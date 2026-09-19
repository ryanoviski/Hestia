package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.TransactionFilter;
import io.github.ryanoviski.hestia.application.dto.TransactionInput;
import io.github.ryanoviski.hestia.application.repositories.CategoryRepository;
import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.application.repositories.TransactionRepository;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionOrigin;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.domain.models.Transaction;
import io.github.ryanoviski.hestia.util.MoneyUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TransactionService {
    private final TransactionRepository repository;
    private final ProfileRepository profiles;
    private final CategoryRepository categories;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, ProfileRepository profiles,
                              CategoryRepository categories, Clock clock) {
        this.repository = repository;
        this.profiles = profiles;
        this.categories = categories;
        this.clock = clock;
    }

    public List<Transaction> search(TransactionFilter filter) {
        return repository.search(profiles.findDefaultHouseholdId(), filter, clock);
    }

    public Transaction find(long id) {
        return repository.findById(id, profiles.findDefaultHouseholdId())
                .orElseThrow(() -> new ValidationException("Movimentação não encontrada."));
    }

    public Transaction create(TransactionInput input) {
        long householdId = profiles.findDefaultHouseholdId();
        Validated validated = validate(input, householdId, null);
        Instant now = Instant.now(clock);
        return repository.insert(toTransaction(null, householdId, input, validated.amountCents(), now, now));
    }

    public Transaction update(long id, TransactionInput input) {
        long householdId = profiles.findDefaultHouseholdId();
        Transaction existing = find(id);
        if (existing.origin() != TransactionOrigin.MANUAL && input.type() != existing.type())
            throw new ValidationException("O tipo de uma movimentação gerada não pode ser alterado.");
        if (existing.origin() == TransactionOrigin.INSTALLMENT
                && MoneyUtils.toCents(input.amount()) != existing.amountCents())
            throw new ValidationException("O valor de uma parcela não pode ser alterado nesta versão.");
        Validated validated = validate(input, householdId, existing);
        return repository.update(toTransaction(id, householdId, input, validated.amountCents(),
                existing.createdAt(), Instant.now(clock)));
    }

    public void settle(long id, LocalDate date) {
        find(id);
        repository.updateStatus(id, profiles.findDefaultHouseholdId(), TransactionStatus.SETTLED,
                date == null ? LocalDate.now(clock) : date);
    }

    public void reopen(long id) {
        find(id);
        repository.updateStatus(id, profiles.findDefaultHouseholdId(), TransactionStatus.PENDING, null);
    }

    public void cancel(long id) {
        find(id);
        repository.updateStatus(id, profiles.findDefaultHouseholdId(), TransactionStatus.CANCELLED, null);
    }

    private Validated validate(TransactionInput input, long householdId, Transaction existing) {
        if (input == null || input.type() == null) throw new ValidationException("Selecione o tipo.");
        String description = input.description() == null ? "" : input.description().trim();
        if (description.isEmpty()) throw new ValidationException("Informe a descrição.");
        if (description.length() > 150) throw new ValidationException("A descrição deve ter no máximo 150 caracteres.");
        if (input.referenceDate() == null) throw new ValidationException("Informe a data de referência.");
        if (input.status() == null) throw new ValidationException("Selecione a situação.");
        String notes = input.notes() == null ? null : input.notes().trim();
        if (notes != null && notes.length() > 1000) throw new ValidationException("As observações devem ter no máximo 1.000 caracteres.");
        if (input.status() == TransactionStatus.SETTLED && input.settlementDate() == null)
            throw new ValidationException("Informe a data de conclusão.");
        if (input.status() != TransactionStatus.SETTLED && input.settlementDate() != null)
            throw new ValidationException("Somente movimentações concluídas possuem data de conclusão.");

        Profile profile = profiles.findById(input.profileId())
                .orElseThrow(() -> new ValidationException("Selecione um perfil válido."));
        if (profile.householdId() != householdId) throw new ValidationException("Perfil inválido para este grupo.");
        if (!profile.active() && (existing == null || existing.profileId() != profile.id()))
            throw new ValidationException("O perfil selecionado está inativo.");

        Category category = categories.findById(input.categoryId(), householdId)
                .orElseThrow(() -> new ValidationException("Selecione uma categoria válida."));
        if (category.householdId() != null && category.householdId() != householdId)
            throw new ValidationException("Categoria inválida para este grupo.");
        if (category.type() != input.type().categoryType())
            throw new ValidationException("A categoria não é compatível com o tipo da movimentação.");
        if (!category.active() && (existing == null || existing.categoryId() != category.id()))
            throw new ValidationException("A categoria selecionada está inativa.");
        return new Validated(MoneyUtils.toCents(input.amount()));
    }

    private Transaction toTransaction(Long id, long householdId, TransactionInput input, long cents,
                                      Instant createdAt, Instant updatedAt) {
        return new Transaction(id, householdId, input.profileId(), input.categoryId(), input.type(),
                input.description().trim(), cents, input.referenceDate(), input.dueDate(),
                input.status() == TransactionStatus.SETTLED ? input.settlementDate() : null,
                input.status(), input.notes() == null || input.notes().isBlank() ? null : input.notes().trim(),
                createdAt, updatedAt, null, null, TransactionOrigin.MANUAL, null, null);
    }

    private record Validated(long amountCents) { }
}
