package io.github.ryanoviski.hestia.domain.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RecurringExpense(Long id, long householdId, long profileId, long categoryId,
                               String description, long amountCents, LocalDate firstDueDate,
                               LocalDate endDate, boolean active, String notes, Instant createdAt,
                               Instant updatedAt, String profileName, String categoryName,
                               LocalDate nextDueDate) {
    public BigDecimal amount() { return BigDecimal.valueOf(amountCents, 2); }
}
