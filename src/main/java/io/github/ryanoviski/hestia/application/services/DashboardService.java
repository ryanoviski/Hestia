package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.DashboardSummary;
import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.application.repositories.TransactionRepository;

import java.time.Clock;
import java.time.YearMonth;

public final class DashboardService {
    private final TransactionRepository transactions;
    private final ProfileRepository profiles;
    private final Clock clock;

    public DashboardService(TransactionRepository transactions, ProfileRepository profiles, Clock clock) {
        this.transactions = transactions;
        this.profiles = profiles;
        this.clock = clock;
    }

    public DashboardSummary summary(YearMonth month) {
        return transactions.summarize(profiles.findDefaultHouseholdId(), month, clock);
    }
}
