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
            Clock clock = Clock.systemDefaultZone();
            var recurringService = new RecurringExpenseService(commitmentRepository, profileRepository, categoryRepository, clock);
            recurringService.ensureGeneratedThrough(java.time.YearMonth.now(clock).plusMonths(12));
            return new ApplicationContext(
                    new ProfileService(profileRepository),
                    new CategoryService(categoryRepository, profileRepository),
                    new TransactionService(transactionRepository, profileRepository, categoryRepository, clock),
                    new DashboardService(transactionRepository, profileRepository, clock),
                    recurringService,
                    new InstallmentPlanService(commitmentRepository, profileRepository, categoryRepository, clock),
                    new CalendarService(transactionRepository, profileRepository, recurringService, clock));
        } catch (Exception exception) {
            throw new ApplicationInitializationException("Could not initialize Hestia", exception);
        }
    }
}
