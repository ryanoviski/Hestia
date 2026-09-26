package io.github.ryanoviski.hestia.application.dto;

import java.util.List;

public record CategoryExpenseReport(String categoryName, List<ReportExpenseItem> items,
                                    long paidCents, long overdueCents) {
    public CategoryExpenseReport {
        items = List.copyOf(items);
    }

    public long totalCents() { return paidCents + overdueCents; }
}
