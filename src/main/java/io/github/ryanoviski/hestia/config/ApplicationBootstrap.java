package io.github.ryanoviski.hestia.config;

import io.github.ryanoviski.hestia.application.services.CategoryService;
import io.github.ryanoviski.hestia.application.services.DashboardService;
import io.github.ryanoviski.hestia.application.services.ProfileService;
import io.github.ryanoviski.hestia.application.services.TransactionService;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseInitializer;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteCategoryRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteProfileRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteTransactionRepository;

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
            Clock clock = Clock.systemDefaultZone();
            return new ApplicationContext(
                    new ProfileService(profileRepository),
                    new CategoryService(categoryRepository, profileRepository),
                    new TransactionService(transactionRepository, profileRepository, categoryRepository, clock),
                    new DashboardService(transactionRepository, profileRepository, clock));
        } catch (Exception exception) {
            throw new ApplicationInitializationException("Could not initialize Hestia", exception);
        }
    }
}
