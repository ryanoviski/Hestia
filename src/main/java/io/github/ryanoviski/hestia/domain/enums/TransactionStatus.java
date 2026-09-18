package io.github.ryanoviski.hestia.domain.enums;

public enum TransactionStatus {
    PENDING, SETTLED, CANCELLED;

    public String displayName(TransactionType type) {
        return switch (this) {
            case PENDING -> type == TransactionType.INCOME ? "Prevista" : "Pendente";
            case SETTLED -> type == TransactionType.INCOME ? "Recebida" : "Paga";
            case CANCELLED -> "Cancelada";
        };
    }
}
