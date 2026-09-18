package io.github.ryanoviski.hestia.domain.enums;

public enum InstallmentPlanStatus {
    IN_PROGRESS("Em andamento"), COMPLETED("Concluído"), PARTIALLY_CANCELLED("Parcialmente cancelado"), CANCELLED("Cancelado");
    private final String displayName;
    InstallmentPlanStatus(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
