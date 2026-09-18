package io.github.ryanoviski.hestia.config;

import io.github.ryanoviski.hestia.application.services.ProfileService;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseInitializer;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteProfileRepository;

public final class ApplicationBootstrap {
    private ApplicationBootstrap() {
    }

    public static ApplicationContext initialize() {
        try {
            ApplicationPaths paths = ApplicationPaths.resolve();
            paths.createDirectories();
            ConnectionFactory connectionFactory = new ConnectionFactory(paths.databaseFile());
            new DatabaseInitializer(connectionFactory).initialize();
            var repository = new SqliteProfileRepository(connectionFactory);
            return new ApplicationContext(new ProfileService(repository));
        } catch (Exception exception) {
            throw new ApplicationInitializationException("Could not initialize Hestia", exception);
        }
    }
}
