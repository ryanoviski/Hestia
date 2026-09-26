package io.github.ryanoviski.hestia.application.repositories;

import io.github.ryanoviski.hestia.application.dto.GeneratedExpense;
import io.github.ryanoviski.hestia.domain.models.Installment;
import io.github.ryanoviski.hestia.domain.models.InstallmentPlan;
import io.github.ryanoviski.hestia.domain.models.RecurringExpense;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface FinancialCommitmentRepository {
    RecurringExpense createRecurring(RecurringExpense rule, List<GeneratedExpense> occurrences);
    RecurringExpense updateRecurring(RecurringExpense rule, YearMonth effectiveMonth,
                                     List<GeneratedExpense> futureOccurrences);
    List<RecurringExpense> findRecurring(long householdId, String search, Long profileId, Long categoryId);
    Optional<RecurringExpense> findRecurringById(long householdId, long id);
    void appendRecurringOccurrences(RecurringExpense rule, List<GeneratedExpense> occurrences);
    void setRecurringActive(long householdId, long id, boolean active, boolean cancelFuture,
                            LocalDate today);
    InstallmentPlan createInstallmentPlan(InstallmentPlan plan, List<GeneratedExpense> installments);
    List<InstallmentPlan> findInstallmentPlans(long householdId);
    List<Installment> findInstallments(long householdId, long planId);
    void cancelRemainingInstallments(long householdId, long planId, LocalDate today);
    boolean deleteInactiveRecurring(long householdId, long id);
    boolean deleteInactiveInstallmentPlan(long householdId, long id);
}
