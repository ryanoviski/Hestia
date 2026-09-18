package io.github.ryanoviski.hestia.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringExpenseInput(String description, BigDecimal amount, long profileId,
                                    long categoryId, LocalDate firstDueDate, LocalDate endDate,
                                    String notes, boolean active) { }
