package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.dto.AttachmentFilter;
import io.github.ryanoviski.hestia.application.services.ProfileService;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.domain.enums.DocumentType;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.presentation.components.ColorPalette;
import io.github.ryanoviski.hestia.presentation.components.ComboBoxSupport;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProfilesController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfilesController.class);

    @FXML private VBox profilesList;
    @FXML private Label emptyState;
    @FXML private Label formTitle;
    @FXML private Label formMessage;
    @FXML private TextField nameField;
    @FXML private ComboBox<ProfileType> typeField;
    @FXML private ColorPalette colorPalette;
    @FXML private Button saveButton;
    @FXML private Button cancelEditButton;

    private ProfileService service;
    private ApplicationContext context;
    private Profile editing;

    @FXML private void initialize() {
        ComboBoxSupport.configure(typeField, ProfileType::displayName);
        typeField.getItems().setAll(ProfileType.values());
        typeField.setValue(ProfileType.PERSON);
    }

    public void configure(ApplicationContext context) {
        this.context = context;
        this.service = context.profileService();
        refresh();
    }

    @FXML private void saveProfile() {
        clearMessage();
        try {
            boolean creating = editing == null;
            if (creating) service.createProfile(nameField.getText(), typeField.getValue(), colorPalette.getSelectedColor());
            else service.updateProfile(editing.id(), nameField.getText(), typeField.getValue(), colorPalette.getSelectedColor());
            cancelEdit();
            showMessage(creating ? "Perfil cadastrado com sucesso." : "Perfil atualizado com sucesso.", false);
            refresh();
        } catch (ValidationException exception) {
            showMessage(exception.getMessage(), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not save profile", exception);
            showMessage("Não foi possível salvar o perfil. Tente novamente.", true);
        }
    }

    @FXML private void cancelEdit() {
        editing = null;
        formTitle.setText("Novo perfil");
        saveButton.setText("Cadastrar perfil");
        nameField.clear();
        typeField.setValue(ProfileType.PERSON);
        colorPalette.setSelectedColor(null);
        cancelEditButton.setVisible(false);
        cancelEditButton.setManaged(false);
    }

    private void edit(Profile profile) {
        editing = profile;
        formTitle.setText("Editar perfil");
        saveButton.setText("Salvar alterações");
        nameField.setText(profile.name());
        typeField.setValue(profile.type());
        colorPalette.setSelectedColor(profile.color());
        cancelEditButton.setVisible(true);
        cancelEditButton.setManaged(true);
        nameField.requestFocus();
    }

    private void refresh() {
        try {
            var profiles = service.listProfiles();
            profilesList.getChildren().clear();
            profiles.forEach(profile -> profilesList.getChildren().add(createRow(profile)));
            emptyState.setVisible(profiles.isEmpty());
            emptyState.setManaged(profiles.isEmpty());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not list profiles", exception);
            showMessage("Não foi possível carregar os perfis.", true);
        }
    }

    private HBox createRow(Profile profile) {
        Label color = new Label();
        color.getStyleClass().add("profile-color");
        String selectedColor = profile.color() == null ? "#3D8B7D" : profile.color();
        if (selectedColor.matches("#[0-9a-fA-F]{6}")) color.setStyle("-fx-background-color: " + selectedColor + ";");

        Label name = new Label(profile.name());
        name.getStyleClass().add("profile-name");
        Label details = new Label(profile.type().displayName() + " · " + (profile.active() ? "Ativo" : "Inativo"));
        details.getStyleClass().add("profile-meta");
        VBox identity = new VBox(3, name, details);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        FlowPane actions = new FlowPane(8, 8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(action("Editar", "secondary-button", () -> edit(profile)));
        actions.getChildren().add(action("Anexos", "secondary-button", () -> manageAttachments(profile)));
        actions.getChildren().add(action(profile.active() ? "Desativar" : "Reativar", "secondary-button", () -> toggle(profile)));
        actions.getChildren().add(action("Excluir", "destructive-button", () -> delete(profile)));
        HBox row = new HBox(14, color, identity, spacer, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-row");
        return row;
    }

    private Button action(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void toggle(Profile profile) {
        try {
            service.setActive(profile.id(), !profile.active());
            showMessage(profile.active() ? "Perfil desativado." : "Perfil reativado.", false);
            refresh();
        } catch (ValidationException exception) {
            showMessage(exception.getMessage(), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not change profile status {}", profile.id(), exception);
            showMessage("Não foi possível alterar o perfil.", true);
        }
    }

    private void delete(Profile profile) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Excluir o perfil \"" + profile.name() + "\"? A exclusão só será permitida se não houver histórico ou anexos vinculados.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setTitle("Excluir perfil");
        confirmation.setHeaderText("Confirme a exclusão permanente");
        ThemeManager.apply(confirmation);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            service.deleteProfile(profile.id());
            if (editing != null && editing.id().equals(profile.id())) cancelEdit();
            showMessage("Perfil excluído.", false);
            refresh();
        } catch (ValidationException exception) {
            showMessage(exception.getMessage(), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not delete profile {}", profile.id(), exception);
            showMessage("Não foi possível excluir o perfil.", true);
        }
    }

    private void manageAttachments(Profile profile) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Anexos · " + profile.name());
        dialog.setHeaderText("Arquivos vinculados ao perfil");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox content = new VBox(10);
        content.setPadding(new Insets(8));
        Button add = new Button("Adicionar arquivos");
        add.getStyleClass().add("primary-button");

        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            var attachments = context.attachmentService().search(new AttachmentFilter(
                    null, profile.id(), null, null, null, null, false, null));
            content.getChildren().removeIf(node -> node != add);
            if (attachments.isEmpty()) content.getChildren().add(new Label("Nenhum anexo neste perfil."));
            attachments.forEach(attachment -> {
                Label label = new Label(attachment.originalFilename() + " · "
                        + attachment.documentType().displayName() + " · " + attachment.integrity().displayName());
                label.setWrapText(true);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Button open = action("Abrir", "table-action", () -> context.attachmentService().openExternal(attachment.id())
                        .exceptionally(error -> { Platform.runLater(() -> showMessage("Não foi possível abrir o anexo.", true)); return null; }));
                Button remove = action("Remover", "destructive-button", () -> {
                    Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                            "Remover o arquivo \"" + attachment.originalFilename() + "\"? O perfil será preservado.",
                            ButtonType.CANCEL, ButtonType.OK);
                    ThemeManager.apply(confirmation);
                    if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        context.attachmentService().remove(attachment.id());
                        load[0].run();
                    }
                });
                HBox attachmentRow = new HBox(8, label, spacer, open, remove);
                attachmentRow.setAlignment(Pos.CENTER_LEFT);
                attachmentRow.getStyleClass().add("transaction-row");
                content.getChildren().add(attachmentRow);
            });
        };

        add.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Adicionar anexos a " + profile.name());
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF e imagens", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
            var files = chooser.showOpenMultipleDialog(profilesList.getScene().getWindow());
            if (files == null) return;
            try {
                for (var file : files) context.attachmentService().importForProfile(profile.id(), DocumentType.OTHER, null, file.toPath());
                load[0].run();
            } catch (RuntimeException exception) {
                showMessage(exception.getMessage(), true);
            }
        });

        content.getChildren().add(add);
        load[0].run();
        dialog.getDialogPane().setContent(new ScrollPane(content));
        ThemeManager.apply(dialog, 700, 520);
        dialog.showAndWait();
    }

    private void clearMessage() {
        formMessage.setText("");
        formMessage.getStyleClass().removeAll("form-error", "form-success");
    }

    private void showMessage(String message, boolean error) {
        formMessage.setText(message == null ? "Não foi possível concluir a operação." : message);
        formMessage.getStyleClass().removeAll("form-error", "form-success");
        formMessage.getStyleClass().add(error ? "form-error" : "form-success");
    }
}
