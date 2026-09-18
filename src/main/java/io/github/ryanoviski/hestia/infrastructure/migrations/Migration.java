package io.github.ryanoviski.hestia.infrastructure.migrations;

public record Migration(int version, String description, String resourcePath) {
}
