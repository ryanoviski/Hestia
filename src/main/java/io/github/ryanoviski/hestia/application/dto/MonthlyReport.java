package io.github.ryanoviski.hestia.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;

public record MonthlyReport(YearMonth period,
                            DashboardSummary current,
                            DashboardSummary previous,
                            Map<String, BigDecimal> expensesByProfile,
                            Instant generatedAt) {
    public BigDecimal totalIncome() {
        return current.receivedIncome().add(current.expectedIncome());
    }

    public BigDecimal totalExpenses() {
        return current.paidExpenses().add(current.pendingExpenses());
    }

    public BigDecimal previousIncome() {
        return previous.receivedIncome().add(previous.expectedIncome());
    }

    public BigDecimal previousExpenses() {
        return previous.paidExpenses().add(previous.pendingExpenses());
    }

    public BigDecimal incomeChange() {
        return totalIncome().subtract(previousIncome());
    }

    public BigDecimal expenseChange() {
        return totalExpenses().subtract(previousExpenses());
    }

    public BigDecimal resultChange() {
        return current.projectedResult().subtract(previous.projectedResult());
    }
}
