package io.github.ryanoviski.hestia.application.dto;

import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionInput(TransactionType type, String description, BigDecimal amount,
                               long profileId, long categoryId, LocalDate referenceDate,
                               LocalDate dueDate, TransactionStatus status,
                               LocalDate settlementDate, String notes) {
}
