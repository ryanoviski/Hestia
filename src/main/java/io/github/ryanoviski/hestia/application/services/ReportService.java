package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.MonthlyReport;
import io.github.ryanoviski.hestia.application.dto.CategoryExpenseReport;
import io.github.ryanoviski.hestia.application.dto.ReportExpenseItem;
import io.github.ryanoviski.hestia.application.dto.TransactionFilter;
import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.application.repositories.TransactionRepository;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.domain.models.Transaction;
import io.github.ryanoviski.hestia.util.MoneyUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

public final class ReportService {
    private final TransactionRepository transactions;
    private final ProfileRepository profiles;
    private final Clock clock;

    public ReportService(TransactionRepository transactions, ProfileRepository profiles, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public MonthlyReport monthly(YearMonth period) {
        YearMonth selected = Objects.requireNonNull(period, "period");
        long householdId = profiles.findDefaultHouseholdId();
        var current = transactions.summarize(householdId, selected, clock);
        var previous = transactions.summarize(householdId, selected.minusMonths(1), clock);
        var expenses = transactions.search(householdId,
                new TransactionFilter(null, selected, TransactionType.EXPENSE, null,
                        null, null, false, false, null), clock);
        var centsByProfile = new LinkedHashMap<String, Long>();
        expenses.stream()
                .filter(item -> item.status() != TransactionStatus.CANCELLED)
                .forEach(item -> centsByProfile.merge(item.profileName(), item.amountCents(), Long::sum));
        var byProfile = new LinkedHashMap<String, java.math.BigDecimal>();
        centsByProfile.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> byProfile.put(entry.getKey(), MoneyUtils.fromCents(entry.getValue())));
        var grouped=new LinkedHashMap<String,List<Transaction>>();
        transactions.findReportExpenses(householdId,selected,clock)
                .forEach(item->grouped.computeIfAbsent(item.categoryName(),ignored->new ArrayList<>()).add(item));
        var details=new ArrayList<CategoryExpenseReport>();
        grouped.forEach((category,items)->{
            long paid=items.stream().filter(item->item.status()==TransactionStatus.SETTLED).mapToLong(item->item.amountCents()).sum();
            long overdue=items.stream().filter(item->item.isOverdue(clock)).mapToLong(item->item.amountCents()).sum();
            var rows=items.stream().map(item->new ReportExpenseItem(item.description(),item.dueDate(),item.settlementDate(),
                    item.amountCents(),item.status()==TransactionStatus.SETTLED?"Pago"
                            :item.isOverdue(clock)?"Vencido":item.status().displayName(TransactionType.EXPENSE))).toList();
            details.add(new CategoryExpenseReport(category,rows,paid,overdue));
        });
        return new MonthlyReport(selected, current, previous,
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byProfile)), details, Instant.now(clock));
    }
}
