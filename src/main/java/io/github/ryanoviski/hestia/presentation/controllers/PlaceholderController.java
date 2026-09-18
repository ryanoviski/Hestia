package io.github.ryanoviski.hestia.presentation.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class PlaceholderController {
    @FXML private Label moduleName;

    public void setModuleName(String name) {
        moduleName.setText(name);
    }
}
