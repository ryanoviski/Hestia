package io.github.ryanoviski.hestia.presentation.components;

import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.Profile;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.util.function.Function;

public final class ComboBoxSupport {
    private ComboBoxSupport() {
    }

    public static void profiles(ComboBox<Profile> comboBox) {
        configure(comboBox, ComboBoxSupport::profileLabel);
    }

    public static void categories(ComboBox<Category> comboBox) {
        configure(comboBox, ComboBoxSupport::categoryLabel);
    }

    public static <T> void configure(ComboBox<T> comboBox, Function<T, String> labelProvider) {
        StringConverter<T> converter = converter(labelProvider);
        comboBox.setConverter(converter);
        comboBox.setCellFactory(list -> cell(converter));
        comboBox.setButtonCell(cell(converter));
        comboBox.setVisibleRowCount(10);
    }

    public static String profileLabel(Profile profile) {
        return profile == null ? "" : profile.name();
    }

    public static String categoryLabel(Category category) {
        return category == null ? "" : category.name();
    }

    private static <T> StringConverter<T> converter(Function<T, String> labelProvider) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : labelProvider.apply(value);
            }

            @Override
            public T fromString(String value) {
                return null;
            }
        };
    }

    private static <T> ListCell<T> cell(StringConverter<T> converter) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : converter.toString(item));
            }
        };
    }
}
