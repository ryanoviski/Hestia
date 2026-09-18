package io.github.ryanoviski.hestia.application.dto;

import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.domain.enums.TransactionOrigin;

import java.time.YearMonth;

public record TransactionFilter(String search, YearMonth month, TransactionType type,
                                TransactionStatus status, Long profileId, Long categoryId,
                                boolean overdueOnly, boolean dueDateAscending, TransactionOrigin origin) {
    public TransactionFilter(String search, YearMonth month, TransactionType type,
                             TransactionStatus status, Long profileId, Long categoryId,
                             boolean overdueOnly, boolean dueDateAscending) {
        this(search, month, type, status, profileId, categoryId, overdueOnly, dueDateAscending, null);
    }
    public static TransactionFilter empty() {
        return new TransactionFilter(null, null, null, null, null, null, false, false, null);
    }
}
