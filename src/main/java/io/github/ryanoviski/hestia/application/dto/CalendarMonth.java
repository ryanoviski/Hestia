package io.github.ryanoviski.hestia.application.dto;

import io.github.ryanoviski.hestia.domain.models.Transaction;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public record CalendarMonth(YearMonth month, Map<LocalDate, List<Transaction>> days,
                            DashboardSummary summary) { }
