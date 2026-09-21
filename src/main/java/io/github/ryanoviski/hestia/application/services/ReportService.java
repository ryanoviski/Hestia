package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.MonthlyReport;
import io.github.ryanoviski.hestia.application.dto.TransactionFilter;
import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.application.repositories.TransactionRepository;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.util.MoneyUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Objects;

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
        return new MonthlyReport(selected, current, previous,
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byProfile)), Instant.now(clock));
    }
}
