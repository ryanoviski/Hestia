package io.github.ryanoviski.hestia.application.dto;

import java.time.LocalDate;
import java.time.YearMonth;

public record GeneratedExpense(YearMonth referenceMonth, int sequence, long amountCents,
                               LocalDate dueDate, String description, String notes) { }
