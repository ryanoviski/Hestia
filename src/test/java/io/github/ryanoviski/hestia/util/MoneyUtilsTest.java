package io.github.ryanoviski.hestia.util;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyUtilsTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"120|120.00|12000", "120,50|120.50|12050", "1.250,90|1250.90|125090", "R$ 1.250,90|1250.90|125090"})
    void parsesBrazilianValuesExactly(String text, String decimal, long cents) {
        BigDecimal value = MoneyUtils.parseBrazilian(text);
        assertThat(value).isEqualByComparingTo(decimal);
        assertThat(MoneyUtils.toCents(value)).isEqualTo(cents);
        assertThat(MoneyUtils.fromCents(cents)).isEqualByComparingTo(decimal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1,00", "abc", "12,345", "1,2,3"})
    void rejectsInvalidValues(String value) {
        assertThatThrownBy(() -> MoneyUtils.parseBrazilian(value)).isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"0|R$ 0,00", "125090|R$ 1.250,90"})
    void formatsWithBrazilianCurrency(long cents, String expected) {
        assertThat(MoneyUtils.formatCents(cents)).isEqualTo(expected);
    }
}
