package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.GeneratedExpense;
import io.github.ryanoviski.hestia.application.dto.InstallmentPlanInput;
import io.github.ryanoviski.hestia.application.repositories.CategoryRepository;
import io.github.ryanoviski.hestia.application.repositories.FinancialCommitmentRepository;
import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Installment;
import io.github.ryanoviski.hestia.domain.models.InstallmentPlan;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class InstallmentPlanService {
    private final FinancialCommitmentRepository repository;
    private final ProfileRepository profiles;
    private final CategoryRepository categories;
    private final Clock clock;
    private final InstallmentDistributionService distribution = new InstallmentDistributionService();
    public InstallmentPlanService(FinancialCommitmentRepository repository, ProfileRepository profiles,
                                  CategoryRepository categories, Clock clock) {
        this.repository=repository; this.profiles=profiles; this.categories=categories; this.clock=clock;
    }
    public InstallmentPlan create(InstallmentPlanInput input) {
        if (input == null || input.firstDueDate() == null) throw new ValidationException("Informe o primeiro vencimento.");
        long householdId=profiles.findDefaultHouseholdId();
        CommitmentValidator.validateReferences(householdId,input.profileId(),input.categoryId(),profiles,categories);
        long total=MoneyUtils.toCents(input.totalAmount());
        List<Long> values=distribution.distribute(total,input.installmentCount());
        Instant now=Instant.now(clock);
        String description=CommitmentValidator.description(input.description());
        var plan=new InstallmentPlan(null,householdId,input.profileId(),input.categoryId(),description,total,
                input.installmentCount(),input.firstDueDate(),true,CommitmentValidator.notes(input.notes()),now,now,
                null,null,0,input.installmentCount(),0,0,total,input.firstDueDate());
        List<GeneratedExpense> generated=new ArrayList<>();
        for(int i=0;i<values.size();i++){
            YearMonth month=YearMonth.from(input.firstDueDate()).plusMonths(i);
            LocalDate due=MonthlyDueDateCalculator.forMonth(input.firstDueDate(),month);
            generated.add(new GeneratedExpense(month,i+1,values.get(i),due,
                    description+" — parcela "+(i+1)+" de "+values.size(),plan.notes()));
        }
        return repository.createInstallmentPlan(plan,generated);
    }
    public List<InstallmentPlan> list(){return repository.findInstallmentPlans(profiles.findDefaultHouseholdId());}
    public List<Installment> installments(long planId){return repository.findInstallments(profiles.findDefaultHouseholdId(),planId);}
    public void cancelRemaining(long planId){repository.cancelRemainingInstallments(profiles.findDefaultHouseholdId(),planId,LocalDate.now(clock));}

    public List<GeneratedExpense> preview(String description, java.math.BigDecimal totalAmount,
                                          int installmentCount, LocalDate firstDueDate) {
        if (firstDueDate == null) throw new ValidationException("Informe o primeiro vencimento.");
        String normalizedDescription = description == null || description.isBlank()
                ? "Compra parcelada" : description.trim();
        List<Long> values = distribution.distribute(MoneyUtils.toCents(totalAmount), installmentCount);
        List<GeneratedExpense> generated = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            YearMonth month = YearMonth.from(firstDueDate).plusMonths(index);
            LocalDate dueDate = MonthlyDueDateCalculator.forMonth(firstDueDate, month);
            generated.add(new GeneratedExpense(month, index + 1, values.get(index), dueDate,
                    normalizedDescription + " — parcela " + (index + 1) + " de " + values.size(), null));
        }
        return List.copyOf(generated);
    }
}
