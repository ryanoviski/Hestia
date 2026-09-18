package io.github.ryanoviski.hestia.domain.models;

import io.github.ryanoviski.hestia.domain.enums.InstallmentPlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InstallmentPlan(Long id, long householdId, long profileId, long categoryId,
                              String description, long totalAmountCents, int installmentCount,
                              LocalDate firstDueDate, boolean active, String notes, Instant createdAt,
                              Instant updatedAt, String profileName, String categoryName,
                              int paidCount, int pendingCount, int cancelledCount,
                              long paidAmountCents, long remainingAmountCents, LocalDate nextDueDate) {
    public BigDecimal totalAmount() { return BigDecimal.valueOf(totalAmountCents, 2); }
    public InstallmentPlanStatus calculatedStatus() {
        if (cancelledCount == installmentCount) return InstallmentPlanStatus.CANCELLED;
        if (pendingCount == 0 && paidCount > 0) return cancelledCount > 0
                ? InstallmentPlanStatus.PARTIALLY_CANCELLED : InstallmentPlanStatus.COMPLETED;
        if (cancelledCount > 0) return InstallmentPlanStatus.PARTIALLY_CANCELLED;
        return InstallmentPlanStatus.IN_PROGRESS;
    }
}
