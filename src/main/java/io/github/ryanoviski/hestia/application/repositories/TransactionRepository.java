package io.github.ryanoviski.hestia.application.repositories;

import io.github.ryanoviski.hestia.application.dto.DashboardSummary;
import io.github.ryanoviski.hestia.application.dto.TransactionFilter;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.models.Transaction;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    Transaction insert(Transaction transaction);
    Transaction update(Transaction transaction);
    Optional<Transaction> findById(long id, long householdId);
    List<Transaction> search(long householdId, TransactionFilter filter, Clock clock);
    void updateStatus(long id, long householdId, TransactionStatus status, LocalDate settlementDate);
    DashboardSummary summarize(long householdId, YearMonth month, Clock clock);
}
