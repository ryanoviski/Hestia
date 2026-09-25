package io.github.ryanoviski.hestia.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationInfoTest {
    @Test
    void exposesTheVersionDefinedByMaven() {
        assertThat(ApplicationInfo.NAME).isEqualTo("Hestia");
        assertThat(ApplicationInfo.VERSION).isEqualTo("1.0.0");
        assertThat(ApplicationInfo.displayName()).isEqualTo("Hestia 1.0.0");
    }
}
