package io.github.ryanoviski.hestia.presentation.components;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyInputModelTest {
    @Test void appendsDigitsFromCentsAndBackspacesNaturally() {
        MoneyInputModel value=new MoneyInputModel();
        value.appendDigit(1);assertThat(value.cents()).isEqualTo(1);
        value.appendDigit(2);assertThat(value.cents()).isEqualTo(12);
        value.appendDigit(3);assertThat(value.cents()).isEqualTo(123);
        value.appendDigit(4);assertThat(value.cents()).isEqualTo(1234);
        value.appendDigit(5);assertThat(value.formatted()).contains("123,45");
        value.backspace();assertThat(value.cents()).isEqualTo(1234);
        value.backspace();assertThat(value.cents()).isEqualTo(123);
        value.backspace();assertThat(value.cents()).isEqualTo(12);
        value.backspace();assertThat(value.cents()).isEqualTo(1);
        value.backspace();assertThat(value.cents()).isZero();
    }

    @Test void parsesSafeBrazilianAndSimpleDecimalPaste() {
        MoneyInputModel value=new MoneyInputModel();
        value.replaceFromPaste("123,45");assertThat(value.cents()).isEqualTo(12345);
        value.replaceFromPaste("R$ 1.234,56");assertThat(value.cents()).isEqualTo(123456);
        value.replaceFromPaste("123.45");assertThat(value.cents()).isEqualTo(12345);
        value.replaceFromPaste("999.999,99");assertThat(value.cents()).isEqualTo(99_999_999);
        value.replaceFromPaste("0");assertThat(value.cents()).isZero();
    }

    @Test void rejectsAmbiguousInvalidNegativeAndExcessivePaste() {
        MoneyInputModel value=new MoneyInputModel(123);
        assertThatThrownBy(()->value.replaceFromPaste("1.23.45")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(()->value.replaceFromPaste("-1,00")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(()->value.replaceFromPaste("1,234")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(()->value.replaceFromPaste("999999999999999999999"))
                .isInstanceOf(ValidationException.class);
        assertThat(value.cents()).isEqualTo(123);
    }
}
