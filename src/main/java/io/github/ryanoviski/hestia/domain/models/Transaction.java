package io.github.ryanoviski.hestia.domain.models;

import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

public record Transaction(Long id, long householdId, long profileId, long categoryId,
                          TransactionType type, String description, long amountCents,
                          LocalDate referenceDate, LocalDate dueDate, LocalDate settlementDate,
                          TransactionStatus status, String notes, Instant createdAt, Instant updatedAt,
                          String profileName, String categoryName) {
    public boolean isOverdue(Clock clock) {
        return type == TransactionType.EXPENSE && status == TransactionStatus.PENDING
                && dueDate != null && dueDate.isBefore(LocalDate.now(clock));
    }

    public BigDecimal amount() { return BigDecimal.valueOf(amountCents, 2); }
}
