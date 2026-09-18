package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.GeneratedExpense;
import io.github.ryanoviski.hestia.application.dto.RecurringExpenseInput;
import io.github.ryanoviski.hestia.application.repositories.CategoryRepository;
import io.github.ryanoviski.hestia.application.repositories.FinancialCommitmentRepository;
import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.RecurringExpense;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class RecurringExpenseService {
    private final FinancialCommitmentRepository repository;
    private final ProfileRepository profiles;
    private final CategoryRepository categories;
    private final Clock clock;

    public RecurringExpenseService(FinancialCommitmentRepository repository, ProfileRepository profiles,
                                   CategoryRepository categories, Clock clock) {
        this.repository = repository; this.profiles = profiles; this.categories = categories; this.clock = clock;
    }
    public RecurringExpense create(RecurringExpenseInput input) {
        long householdId = profiles.findDefaultHouseholdId();
        RecurringExpense rule = validated(null, householdId, input, Instant.now(clock), Instant.now(clock));
        return repository.createRecurring(rule, planned(rule, YearMonth.now(clock).plusMonths(12)));
    }
    public RecurringExpense update(long id, RecurringExpenseInput input) {
        long householdId = profiles.findDefaultHouseholdId();
        RecurringExpense old = repository.findRecurringById(householdId, id)
                .orElseThrow(() -> new ValidationException("Recorrência não encontrada."));
        RecurringExpense updated = validated(id, householdId, input, old.createdAt(), Instant.now(clock));
        return repository.updateRecurring(updated, YearMonth.now(clock),
                planned(updated, YearMonth.now(clock).plusMonths(12)));
    }
    public List<RecurringExpense> search(String search, Long profileId, Long categoryId) {
        return repository.findRecurring(profiles.findDefaultHouseholdId(), search, profileId, categoryId);
    }
    public void ensureGeneratedThrough(YearMonth month) {
        repository.findRecurring(profiles.findDefaultHouseholdId(), null, null, null).stream()
                .filter(RecurringExpense::active)
                .forEach(rule -> repository.appendRecurringOccurrences(rule, planned(rule, month)));
    }
    public void setActive(long id, boolean active, boolean cancelFuturePending) {
        repository.setRecurringActive(profiles.findDefaultHouseholdId(), id, active, cancelFuturePending,
                LocalDate.now(clock));
    }
    private RecurringExpense validated(Long id, long householdId, RecurringExpenseInput input,
                                       Instant createdAt, Instant updatedAt) {
        if (input == null || input.firstDueDate() == null) throw new ValidationException("Informe o primeiro vencimento.");
        if (input.endDate() != null && input.endDate().isBefore(input.firstDueDate()))
            throw new ValidationException("A data final não pode ser anterior ao primeiro vencimento.");
        CommitmentValidator.validateReferences(householdId, input.profileId(), input.categoryId(), profiles, categories);
        return new RecurringExpense(id, householdId, input.profileId(), input.categoryId(),
                CommitmentValidator.description(input.description()), MoneyUtils.toCents(input.amount()),
                input.firstDueDate(), input.endDate(), input.active(), CommitmentValidator.notes(input.notes()),
                createdAt, updatedAt, null, null, null);
    }
    private List<GeneratedExpense> planned(RecurringExpense rule, YearMonth through) {
        List<GeneratedExpense> result = new ArrayList<>();
        YearMonth month = YearMonth.from(rule.firstDueDate());
        YearMonth end = rule.endDate() == null ? through : YearMonth.from(rule.endDate()).isBefore(through)
                ? YearMonth.from(rule.endDate()) : through;
        while (!month.isAfter(end)) {
            LocalDate due = MonthlyDueDateCalculator.forMonth(rule.firstDueDate(), month);
            if (rule.endDate() == null || !due.isAfter(rule.endDate()))
                result.add(new GeneratedExpense(month, 0, rule.amountCents(), due, rule.description(), rule.notes()));
            month = month.plusMonths(1);
        }
        return result;
    }
}
