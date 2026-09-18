package io.github.ryanoviski.hestia.config;

import io.github.ryanoviski.hestia.application.services.CategoryService;
import io.github.ryanoviski.hestia.application.services.DashboardService;
import io.github.ryanoviski.hestia.application.services.ProfileService;
import io.github.ryanoviski.hestia.application.services.TransactionService;
import io.github.ryanoviski.hestia.application.services.RecurringExpenseService;
import io.github.ryanoviski.hestia.application.services.InstallmentPlanService;
import io.github.ryanoviski.hestia.application.services.CalendarService;

public record ApplicationContext(ProfileService profileService, CategoryService categoryService,
                                 TransactionService transactionService, DashboardService dashboardService,
                                 RecurringExpenseService recurringExpenseService,
                                 InstallmentPlanService installmentPlanService,
                                 CalendarService calendarService) {
}
