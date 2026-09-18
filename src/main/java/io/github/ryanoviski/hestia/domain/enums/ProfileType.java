package io.github.ryanoviski.hestia.domain.enums;

public enum ProfileType {
    PERSON("Pessoa"),
    SHARED("Compartilhado");

    private final String displayName;

    ProfileType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
