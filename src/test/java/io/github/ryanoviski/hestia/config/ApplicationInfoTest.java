package io.github.ryanoviski.hestia.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationInfoTest {
    @Test
    void exposesTheVersionDefinedByMaven() {
        assertThat(ApplicationInfo.NAME).isEqualTo("Hestia");
        assertThat(ApplicationInfo.VERSION).isEqualTo("0.1.0-SNAPSHOT");
        assertThat(ApplicationInfo.displayName()).isEqualTo("Hestia 0.1.0-SNAPSHOT");
    }
}
