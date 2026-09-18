package io.github.ryanoviski.hestia.util;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyUtils {
    public static final long MAX_CENTS = 999_999_999_999_99L;
    private static final Locale BRAZIL = Locale.of("pt", "BR");

    private MoneyUtils() { }

    public static BigDecimal parseBrazilian(String input) {
        if (input == null || input.isBlank()) throw new ValidationException("Informe o valor.");
        String normalized = input.trim().replace("R$", "").replace(" ", "")
                .replace(".", "").replace(',', '.');
        try {
            BigDecimal value = new BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY);
            toCents(value);
            return value;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new ValidationException("Informe um valor válido, como 1.250,90.");
        }
    }

    public static long toCents(BigDecimal value) {
        if (value == null) throw new ValidationException("Informe o valor.");
        try {
            long cents = value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
            if (cents <= 0) throw new ValidationException("O valor deve ser maior que zero.");
            if (cents > MAX_CENTS) throw new ValidationException("O valor informado excede o limite permitido.");
            return cents;
        } catch (ArithmeticException exception) {
            throw new ValidationException("O valor deve possuir no máximo duas casas decimais.");
        }
    }

    public static BigDecimal fromCents(long cents) { return BigDecimal.valueOf(cents, 2); }

    public static String format(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(BRAZIL).format(value);
    }

    public static String formatCents(long cents) { return format(fromCents(cents)); }
}
