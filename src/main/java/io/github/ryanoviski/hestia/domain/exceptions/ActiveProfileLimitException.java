package io.github.ryanoviski.hestia.domain.exceptions;

public final class ActiveProfileLimitException extends ValidationException {
    public ActiveProfileLimitException() {
        super("O grupo já possui o limite de cinco perfis ativos.");
    }
}
