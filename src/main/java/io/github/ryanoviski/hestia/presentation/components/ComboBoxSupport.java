package io.github.ryanoviski.hestia.presentation.components;

import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.Profile;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

import java.util.function.Function;

public final class ComboBoxSupport {
    private ComboBoxSupport() {
    }

    public static void profiles(ComboBox<Profile> comboBox) {
        configureColored(comboBox, ComboBoxSupport::profileLabel, Profile::color);
    }

    public static void categories(ComboBox<Category> comboBox) {
        configureColored(comboBox, ComboBoxSupport::categoryLabel, Category::color);
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

    private static <T> void configureColored(ComboBox<T> comboBox, Function<T, String> labelProvider,
                                             Function<T, String> colorProvider) {
        StringConverter<T> converter = converter(labelProvider);
        comboBox.setConverter(converter);
        comboBox.setCellFactory(list -> coloredCell(converter, colorProvider));
        comboBox.setButtonCell(coloredCell(converter, colorProvider));
        comboBox.setVisibleRowCount(10);
    }

    private static <T> ListCell<T> coloredCell(StringConverter<T> converter, Function<T, String> colorProvider) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Region color = new Region();
                color.getStyleClass().add("combo-color-dot");
                String value = colorProvider.apply(item);
                if (value != null && value.matches("#[0-9a-fA-F]{6}")) {
                    color.setStyle("-fx-background-color: " + value + ";");
                }
                Label label = new Label(converter.toString(item));
                label.getStyleClass().add("combo-item-label");
                label.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(label, Priority.ALWAYS);
                HBox row = new HBox(8, color, label);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setMaxWidth(Double.MAX_VALUE);
                setGraphic(row);
            }
        };
    }
}
