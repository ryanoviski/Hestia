package io.github.ryanoviski.hestia.presentation.components;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DatePickerSupport {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DatePickerSupport() {
    }

    public static DatePicker configure(DatePicker picker) {
        picker.setPromptText("dd/mm/aaaa");
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate value) {
                return value == null ? "" : FORMATTER.format(value);
            }

            @Override
            public LocalDate fromString(String value) {
                if (value == null || value.isBlank()) return null;
                try {
                    return LocalDate.parse(value.trim(), FORMATTER);
                } catch (DateTimeParseException exception) {
                    return null;
                }
            }
        });
        return picker;
    }
}
