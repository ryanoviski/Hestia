package io.github.ryanoviski.hestia.presentation.components;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Objects;

public final class ThemeManager {
    private static final String STYLESHEET = stylesheet();

    private ThemeManager() {
    }

    public static void apply(Scene scene) {
        addStylesheet(scene.getStylesheets());
    }

    public static void apply(Parent parent) {
        addStylesheet(parent.getStylesheets());
    }

    public static void apply(DialogPane dialogPane) {
        addStylesheet(dialogPane.getStylesheets());
        if (!dialogPane.getStyleClass().contains("hestia-dialog")) {
            dialogPane.getStyleClass().add("hestia-dialog");
        }
    }

    public static void apply(Dialog<?> dialog) {
        apply(dialog.getDialogPane());
        dialog.setResizable(true);
    }

    public static void apply(Dialog<?> dialog, double preferredWidth, double preferredHeight) {
        apply(dialog);
        DialogPane pane = dialog.getDialogPane();
        pane.setMinWidth(Math.min(preferredWidth, 480));
        pane.setPrefWidth(preferredWidth);
        if (preferredHeight > 0) {
            pane.setMinHeight(Math.min(preferredHeight, 320));
            pane.setPrefHeight(preferredHeight);
        }
    }

    public static void apply(Stage stage) {
        if (stage.getScene() != null) {
            apply(stage.getScene());
        }
    }

    public static String stylesheetUrl() {
        return STYLESHEET;
    }

    private static void addStylesheet(java.util.List<String> stylesheets) {
        if (!stylesheets.contains(STYLESHEET)) {
            stylesheets.add(STYLESHEET);
        }
    }

    private static String stylesheet() {
        URL resource = ThemeManager.class.getResource("/styles/main.css");
        return Objects.requireNonNull(resource, "Required stylesheet /styles/main.css was not found")
                .toExternalForm();
    }
}
