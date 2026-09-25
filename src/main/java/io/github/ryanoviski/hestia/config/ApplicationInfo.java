package io.github.ryanoviski.hestia.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ApplicationInfo {
    private static final Properties PROPERTIES = loadProperties();

    public static final String NAME = PROPERTIES.getProperty("application.name", "Hestia");
    public static final String VERSION = PROPERTIES.getProperty("application.version", "development");

    private ApplicationInfo() {
    }

    public static String displayName() {
        return NAME + " " + VERSION;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream stream = ApplicationInfo.class.getResourceAsStream("/hestia.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Required application metadata was not found");
            }
            properties.load(stream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load application metadata", exception);
        }
    }
}
