package io.github.ryanoviski.hestia.presentation.components;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;

import java.util.List;

public final class ColorPalette extends FlowPane {
    public static final List<String> COLORS = List.of(
            "#2F7F77", "#3D8B7D", "#3F6FA0", "#6558A6", "#A85C8B",
            "#C45E52", "#D17B32", "#C39A3B", "#68737D", "#4F7A55",
            "#2E8792", "#8B5E3C");
    private static final List<String> NAMES = List.of(
            "Verde-petróleo", "Verde suave", "Azul", "Violeta", "Rosa",
            "Coral", "Laranja", "Dourado", "Cinza", "Verde-folha",
            "Azul-petróleo", "Terracota");

    private final ToggleGroup group = new ToggleGroup();
    private final ObjectProperty<String> selectedColor = new SimpleObjectProperty<>();

    public ColorPalette() {
        setHgap(9);
        setVgap(9);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("color-palette");

        for (int index = 0; index < COLORS.size(); index++) addSwatch(COLORS.get(index), NAMES.get(index));

        group.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null && oldValue != null) {
                group.selectToggle(oldValue);
            } else if (newValue != null) {
                selectedColor.set((String) newValue.getUserData());
            }
        });
        setSelectedColor(COLORS.get(1));
    }

    public String getSelectedColor() {
        return selectedColor.get();
    }

    public void setSelectedColor(String color) {
        String normalized = color == null || !color.trim().toUpperCase().matches("#[0-9A-F]{6}")
                ? COLORS.get(1) : color.trim().toUpperCase();
        getChildren().removeIf(node -> node instanceof ToggleButton button
                && !COLORS.contains((String) button.getUserData())
                && !normalized.equals(button.getUserData()));
        boolean exists = getChildren().stream().filter(ToggleButton.class::isInstance)
                .map(ToggleButton.class::cast).anyMatch(button -> normalized.equals(button.getUserData()));
        if (!exists) addSwatch(normalized, "Cor atual");
        getChildren().stream()
                .filter(ToggleButton.class::isInstance)
                .map(ToggleButton.class::cast)
                .filter(button -> normalized.equals(button.getUserData()))
                .findFirst()
                .ifPresent(group::selectToggle);
    }

    private void addSwatch(String color, String name) {
        ToggleButton swatch = new ToggleButton();
        swatch.setToggleGroup(group);
        swatch.setUserData(color);
        swatch.setAccessibleText("Selecionar cor " + name);
        swatch.setTooltip(new Tooltip(name));
        swatch.getStyleClass().add("color-swatch");
        swatch.setStyle("-fx-background-color: " + color + ";");
        getChildren().add(swatch);
    }

    public ObjectProperty<String> selectedColorProperty() {
        return selectedColor;
    }
}
