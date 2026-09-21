package io.github.ryanoviski.hestia.config;

import io.github.ryanoviski.hestia.application.services.CategoryService;
import io.github.ryanoviski.hestia.application.services.DashboardService;
import io.github.ryanoviski.hestia.application.services.ProfileService;
import io.github.ryanoviski.hestia.application.services.TransactionService;
import io.github.ryanoviski.hestia.application.services.RecurringExpenseService;
import io.github.ryanoviski.hestia.application.services.InstallmentPlanService;
import io.github.ryanoviski.hestia.application.services.CalendarService;
import io.github.ryanoviski.hestia.application.services.AttachmentService;
import io.github.ryanoviski.hestia.application.services.DocumentPreviewService;
import io.github.ryanoviski.hestia.application.services.BackupService;
import io.github.ryanoviski.hestia.application.services.BackupPreferencesService;
import io.github.ryanoviski.hestia.application.services.ReportService;
import io.github.ryanoviski.hestia.application.services.ReportPdfService;

public record ApplicationContext(ProfileService profileService, CategoryService categoryService,
                                 TransactionService transactionService, DashboardService dashboardService,
                                 RecurringExpenseService recurringExpenseService,
                                 InstallmentPlanService installmentPlanService,
                                 CalendarService calendarService, AttachmentService attachmentService,
                                 DocumentPreviewService documentPreviewService, BackupService backupService,
                                 BackupPreferencesService backupPreferencesService,
                                 ReportService reportService, ReportPdfService reportPdfService) {
}
