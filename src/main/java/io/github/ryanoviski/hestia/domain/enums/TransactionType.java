package io.github.ryanoviski.hestia.domain.enums;

public enum TransactionType {
    INCOME("Receita"), EXPENSE("Despesa");

    private final String displayName;
    TransactionType(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
    public CategoryType categoryType() { return CategoryType.valueOf(name()); }
    @Override public String toString() { return displayName; }
}
