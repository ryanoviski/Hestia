package io.github.ryanoviski.hestia.application.dto;

import java.time.LocalDate;

public record ReportExpenseItem(String description, LocalDate dueDate, LocalDate settlementDate,
                                long amountCents, String status) { }
