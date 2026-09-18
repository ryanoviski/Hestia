package io.github.ryanoviski.hestia.domain.enums;

public enum CategoryType {
    INCOME("Receita"),
    EXPENSE("Despesa");

    private final String displayName;

    CategoryType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override public String toString() { return displayName; }
}
