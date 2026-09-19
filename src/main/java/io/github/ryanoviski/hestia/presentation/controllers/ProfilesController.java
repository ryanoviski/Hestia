package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.dto.AttachmentFilter;
import io.github.ryanoviski.hestia.application.services.ProfileService;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.domain.enums.DocumentType;
import javafx.stage.FileChooser;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Profile;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProfilesController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfilesController.class);

    @FXML private VBox profilesList;
    @FXML private Label emptyState;
    @FXML private Label formMessage;
    @FXML private TextField nameField;
    @FXML private ChoiceBox<ProfileType> typeField;
    @FXML private TextField colorField;

    private ProfileService service;
    private ApplicationContext context;

    @FXML
    private void initialize() {
        typeField.getItems().setAll(ProfileType.values());
        typeField.setValue(ProfileType.PERSON);
    }

    public void configure(ApplicationContext context) {
        this.context = context;
        this.service = context.profileService();
        refresh();
    }

    @FXML
    private void createProfile() {
        clearMessage();
        try {
            service.createProfile(nameField.getText(), typeField.getValue(), colorField.getText());
            nameField.clear();
            colorField.clear();
            typeField.setValue(ProfileType.PERSON);
            showMessage("Perfil cadastrado com sucesso.", false);
            refresh();
        } catch (ValidationException exception) {
            showMessage(exception.getMessage(), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not create profile", exception);
            showMessage("Não foi possível salvar o perfil. Tente novamente.", true);
        }
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
        if (selectedColor.matches("#[0-9a-fA-F]{6}")) {
            color.setStyle("-fx-background-color: " + selectedColor + ";");
        }

        VBox identity = new VBox(3, new Label(profile.name()),
                new Label(profile.type().displayName() + (profile.active() ? "" : " · Inativo")));
        identity.getStyleClass().add("profile-identity");
        identity.getChildren().get(0).getStyleClass().add("profile-name");
        identity.getChildren().get(1).getStyleClass().add("profile-meta");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(14, color, identity, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-row");

        if (profile.active()) {
            Button deactivate = new Button("Desativar");
            deactivate.getStyleClass().add("secondary-button");
            deactivate.setOnAction(event -> {
                try {
                    service.deactivateProfile(profile.id());
                    showMessage("Perfil desativado.", false);
                    refresh();
                } catch (RuntimeException exception) {
                    LOGGER.error("Could not deactivate profile {}", profile.id(), exception);
                    showMessage("Não foi possível desativar o perfil.", true);
                }
            });
            row.getChildren().add(deactivate);
        }
        Button documents = new Button("Anexos");
        documents.getStyleClass().add("secondary-button");
        documents.setOnAction(event -> manageAttachments(profile));
        row.getChildren().add(documents);
        return row;
    }

    private void manageAttachments(Profile profile) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Anexos · " + profile.name());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox content = new VBox(8);
        content.setPadding(new Insets(8));
        Button add = new Button("Adicionar arquivos");
        add.getStyleClass().add("primary-button");

        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            var attachments = context.attachmentService().search(new AttachmentFilter(
                    null, profile.id(), null, null, null, null, false, null));
            content.getChildren().removeIf(node -> node != add);
            if (attachments.isEmpty()) {
                content.getChildren().add(new Label("Nenhum anexo neste perfil."));
            }
            attachments.forEach(attachment -> {
                Label label = new Label(attachment.originalFilename() + " · "
                        + attachment.documentType().displayName() + " · " + attachment.integrity().displayName());
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Button open = new Button("Abrir");
                open.getStyleClass().add("table-action");
                open.setOnAction(event -> context.attachmentService().openExternal(attachment.id())
                        .exceptionally(error -> {
                            javafx.application.Platform.runLater(() -> showMessage(
                                    "Não foi possível abrir o anexo.", true));
                            return null;
                        }));
                Button remove = new Button("Remover");
                remove.getStyleClass().add("table-action");
                remove.setOnAction(event -> {
                    Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                            "Remover este anexo? O perfil será preservado.", ButtonType.CANCEL, ButtonType.OK);
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
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "PDF e imagens", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
            var files = chooser.showOpenMultipleDialog(profilesList.getScene().getWindow());
            if (files == null) return;
            try {
                for (var file : files) {
                    context.attachmentService().importForProfile(
                            profile.id(), DocumentType.OTHER, null, file.toPath());
                }
                load[0].run();
            } catch (RuntimeException exception) {
                showMessage(exception.getMessage(), true);
            }
        });

        content.getChildren().add(add);
        load[0].run();
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(620);
        dialog.showAndWait();
    }

    private void clearMessage() {
        formMessage.setText("");
        formMessage.getStyleClass().removeAll("form-error", "form-success");
    }

    private void showMessage(String message, boolean error) {
        formMessage.setText(message);
        formMessage.getStyleClass().removeAll("form-error", "form-success");
        formMessage.getStyleClass().add(error ? "form-error" : "form-success");
    }
}
