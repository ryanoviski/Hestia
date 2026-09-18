package io.github.ryanoviski.hestia.domain.models;

import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import java.time.LocalDate;

public record Installment(long id, long planId, long transactionId, int number,
                          long plannedAmountCents, LocalDate dueDate, TransactionStatus status) { }
