package io.github.ryanoviski.hestia.application.services;

import java.time.LocalDate;
import java.time.YearMonth;

public final class MonthlyDueDateCalculator {
    private MonthlyDueDateCalculator() { }
    public static LocalDate forMonth(LocalDate firstDueDate, YearMonth month) {
        return month.atDay(Math.min(firstDueDate.getDayOfMonth(), month.lengthOfMonth()));
    }
}
