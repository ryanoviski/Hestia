package io.github.ryanoviski.hestia.config;

import io.github.ryanoviski.hestia.application.services.CategoryService;
import io.github.ryanoviski.hestia.application.services.DashboardService;
import io.github.ryanoviski.hestia.application.services.ProfileService;
import io.github.ryanoviski.hestia.application.services.TransactionService;

public record ApplicationContext(ProfileService profileService, CategoryService categoryService,
                                 TransactionService transactionService, DashboardService dashboardService) {
}
