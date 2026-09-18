package io.github.ryanoviski.hestia.config;

import io.github.ryanoviski.hestia.application.services.CategoryService;
import io.github.ryanoviski.hestia.application.services.DashboardService;
import io.github.ryanoviski.hestia.application.services.ProfileService;
import io.github.ryanoviski.hestia.application.services.TransactionService;
import io.github.ryanoviski.hestia.application.services.RecurringExpenseService;
import io.github.ryanoviski.hestia.application.services.InstallmentPlanService;
import io.github.ryanoviski.hestia.application.services.CalendarService;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseInitializer;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteCategoryRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteProfileRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteTransactionRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteFinancialCommitmentRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteAttachmentRepository;
import io.github.ryanoviski.hestia.infrastructure.filesystem.FileValidationService;
import io.github.ryanoviski.hestia.infrastructure.filesystem.SafeAttachmentStorage;
import io.github.ryanoviski.hestia.application.services.AttachmentService;
import io.github.ryanoviski.hestia.application.services.DocumentPreviewService;
import io.github.ryanoviski.hestia.application.services.BackupService;
import io.github.ryanoviski.hestia.application.services.BackupPreferencesService;

import java.time.Clock;

public final class ApplicationBootstrap {
    private ApplicationBootstrap() {
    }

    public static ApplicationContext initialize() {
        try {
            ApplicationPaths paths = ApplicationPaths.resolve();
            paths.createDirectories();
            ConnectionFactory connectionFactory = new ConnectionFactory(paths.databaseFile());
            new DatabaseInitializer(connectionFactory).initialize();
            var profileRepository = new SqliteProfileRepository(connectionFactory);
            var categoryRepository = new SqliteCategoryRepository(connectionFactory);
            var transactionRepository = new SqliteTransactionRepository(connectionFactory);
            var commitmentRepository = new SqliteFinancialCommitmentRepository(connectionFactory);
            var attachmentRepository = new SqliteAttachmentRepository(connectionFactory);
            Clock clock = Clock.systemDefaultZone();
            var fileValidator = new FileValidationService();
            var storage = new SafeAttachmentStorage(paths.attachmentsDirectory(), paths.temporaryDirectory());
            var attachmentService = new AttachmentService(attachmentRepository, profileRepository,
                    transactionRepository, storage, fileValidator, clock);
            var backupPreferences = new BackupPreferencesService(paths.dataDirectory());
            var backupService = new BackupService(paths, connectionFactory, attachmentRepository,
                    profileRepository, storage, fileValidator, backupPreferences, clock);
            var recurringService = new RecurringExpenseService(commitmentRepository, profileRepository, categoryRepository, clock);
            recurringService.ensureGeneratedThrough(java.time.YearMonth.now(clock).plusMonths(12));
            backupService.runAutomaticIfDue();
            return new ApplicationContext(
                    new ProfileService(profileRepository),
                    new CategoryService(categoryRepository, profileRepository),
                    new TransactionService(transactionRepository, profileRepository, categoryRepository, clock),
                    new DashboardService(transactionRepository, profileRepository, clock),
                    recurringService,
                    new InstallmentPlanService(commitmentRepository, profileRepository, categoryRepository, clock),
                    new CalendarService(transactionRepository, profileRepository, recurringService, clock),
                    attachmentService, new DocumentPreviewService(), backupService, backupPreferences);
        } catch (Exception exception) {
            throw new ApplicationInitializationException("Could not initialize Hestia", exception);
        }
    }
}
