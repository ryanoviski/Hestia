package io.github.ryanoviski.hestia.presentation.components;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.util.MoneyUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure, testable state used by the bank-style money field. */
public final class MoneyInputModel {
    private long cents;

    public MoneyInputModel() { this(0); }

    public MoneyInputModel(long cents) {
        if (cents < 0 || cents > MoneyUtils.MAX_CENTS)
            throw new ValidationException("O valor informado excede o limite permitido.");
        this.cents = cents;
    }

    public void appendDigit(int digit) {
        if (digit < 0 || digit > 9) throw new IllegalArgumentException("digit");
        if (cents > (MoneyUtils.MAX_CENTS - digit) / 10)
            throw new ValidationException("O valor informado excede o limite permitido.");
        cents = cents * 10 + digit;
    }

    public void backspace() { cents /= 10; }

    public void clear() { cents=0; }

    public void replaceFromPaste(String text) { cents = parseCents(text); }

    public long cents() { return cents; }

    public BigDecimal amount() { return MoneyUtils.fromCents(cents); }

    public String formatted() { return MoneyUtils.formatCents(cents); }

    static long parseCents(String text) {
        if (text == null || text.isBlank()) throw invalid();
        String value = text.trim().replace("R$", "").replace("\u00A0", "").replace(" ", "");
        if (value.startsWith("+") || value.startsWith("-")) throw invalid();
        String normalized;
        if (value.contains(",")) {
            if (!value.matches("(?:\\d{1,3}(?:\\.\\d{3})*|\\d+)(?:,\\d{1,2})?")) throw invalid();
            normalized = value.replace(".", "").replace(',', '.');
        } else if (value.contains(".")) {
            if (value.matches("\\d{1,3}(?:\\.\\d{3})+")) normalized = value.replace(".", "");
            else if (value.matches("\\d+\\.\\d{1,2}")) normalized = value;
            else throw invalid();
        } else {
            if (!value.matches("\\d+")) throw invalid();
            normalized = value;
        }
        try {
            BigDecimal amount = new BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY);
            long result = amount.movePointRight(2).longValueExact();
            if (result < 0 || result > MoneyUtils.MAX_CENTS) throw invalid();
            return result;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid();
        }
    }

    private static ValidationException invalid() {
        return new ValidationException("Cole um valor válido, como 123,45 ou R$ 123,45.");
    }
}
