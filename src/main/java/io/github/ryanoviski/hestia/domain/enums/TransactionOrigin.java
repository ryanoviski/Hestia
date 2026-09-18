package io.github.ryanoviski.hestia.domain.enums;

public enum TransactionOrigin {
    MANUAL("Manual"), RECURRING("Recorrente"), INSTALLMENT("Parcelada");

    private final String displayName;
    TransactionOrigin(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
