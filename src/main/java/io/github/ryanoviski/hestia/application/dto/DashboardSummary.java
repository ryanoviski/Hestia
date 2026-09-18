package io.github.ryanoviski.hestia.application.dto;

import io.github.ryanoviski.hestia.domain.models.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSummary(BigDecimal receivedIncome, BigDecimal expectedIncome,
                               BigDecimal paidExpenses, BigDecimal pendingExpenses,
                               BigDecimal overdueExpenses, BigDecimal realizedResult,
                               BigDecimal projectedResult, Map<String, BigDecimal> expensesByCategory,
                               List<Transaction> upcomingDue) {
}
