package io.github.ryanoviski.hestia.config;

import io.github.ryanoviski.hestia.application.services.ProfileService;

public record ApplicationContext(ProfileService profileService) {
}
