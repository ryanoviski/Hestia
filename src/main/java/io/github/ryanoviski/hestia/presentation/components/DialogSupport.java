package io.github.ryanoviski.hestia.presentation.components;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

public final class DialogSupport {
    private DialogSupport() {
    }

    public static ButtonType primaryAction(String text) {
        return new ButtonType(text, ButtonBar.ButtonData.OK_DONE);
    }

    public static ButtonType destructiveAction(String text) {
        return new ButtonType(text, ButtonBar.ButtonData.OTHER);
    }

    public static void prepare(Dialog<?> dialog, Node ownerNode, double width, double height) {
        Window owner = ownerNode == null || ownerNode.getScene() == null ? null : ownerNode.getScene().getWindow();
        if (owner != null && dialog.getOwner() == null) {
            dialog.initOwner(owner);
        }
        if (dialog.getModality() == Modality.NONE) {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }
        ThemeManager.apply(dialog, width, height);
        styleButtons(dialog);
    }

    public static VBox content(String subtitle, Node... sections) {
        VBox content = new VBox(16);
        content.getStyleClass().add("dialog-content");
        if (subtitle != null && !subtitle.isBlank()) {
            Label description = new Label(subtitle);
            description.setWrapText(true);
            description.getStyleClass().add("dialog-subtitle");
            content.getChildren().add(description);
        }
        content.getChildren().addAll(sections);
        return content;
    }

    public static VBox section(String title, String description, Node... children) {
        VBox section = new VBox(10);
        section.getStyleClass().add("form-section");
        Label heading = new Label(title);
        heading.getStyleClass().add("form-section-title");
        section.getChildren().add(heading);
        if (description != null && !description.isBlank()) {
            Label help = new Label(description);
            help.setWrapText(true);
            help.getStyleClass().add("form-section-help");
            section.getChildren().add(help);
        }
        section.getChildren().addAll(children);
        return section;
    }

    public static VBox field(String label, Node control, boolean required) {
        Label title = new Label(label);
        title.getStyleClass().add("field-label");
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        if (required) control.setAccessibleHelp("Campo obrigatório");
        VBox field = new VBox(5, title, control);
        field.getStyleClass().add("form-field");
        HBox.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    public static HBox columns(Node... fields) {
        HBox row = new HBox(12, fields);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("form-columns");
        for (Node field : fields) {
            if (field instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(region, Priority.ALWAYS);
            }
        }
        return row;
    }

    public static ScrollPane scrollRegion(Node content, double preferredHeight) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(preferredHeight);
        scroll.setMaxHeight(preferredHeight);
        scroll.getStyleClass().add("dialog-scroll-region");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    public static Label statusBadge(String text, String semanticClass) {
        Label badge = new Label(text.toUpperCase());
        badge.getStyleClass().addAll("status-pill", semanticClass);
        return badge;
    }

    public static boolean confirm(Node owner, String title, String heading, String message,
                                  String actionText, boolean destructive) {
        ButtonType action = destructive ? destructiveAction(actionText) : primaryAction(actionText);
        ButtonType cancel = new ButtonType("Voltar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, message, cancel, action);
        dialog.setTitle(title);
        dialog.setHeaderText(heading);
        prepare(dialog, owner, 500, 0);
        if (destructive) {
            Button button = (Button) dialog.getDialogPane().lookupButton(action);
            button.getStyleClass().remove("primary-button");
            button.getStyleClass().add("destructive-button");
        }
        return dialog.showAndWait().orElse(cancel) == action;
    }

    public static void markDestructive(Dialog<?> dialog, ButtonType type) {
        Button button = (Button) dialog.getDialogPane().lookupButton(type);
        if (button != null) {
            button.getStyleClass().remove("primary-button");
            button.getStyleClass().add("destructive-button");
        }
    }

    private static void styleButtons(Dialog<?> dialog) {
        for (ButtonType type : dialog.getDialogPane().getButtonTypes()) {
            Node node = dialog.getDialogPane().lookupButton(type);
            if (!(node instanceof Button button)) continue;
            button.getStyleClass().removeAll("primary-button", "ghost-button");
            if (type.getButtonData().isDefaultButton()) {
                button.getStyleClass().add("primary-button");
            } else {
                button.getStyleClass().add("ghost-button");
            }
        }
    }
}
