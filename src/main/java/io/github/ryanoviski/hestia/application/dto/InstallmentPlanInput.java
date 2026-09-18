package io.github.ryanoviski.hestia.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentPlanInput(String description, BigDecimal totalAmount, int installmentCount,
                                   long profileId, long categoryId, LocalDate firstDueDate,
                                   String notes) { }
