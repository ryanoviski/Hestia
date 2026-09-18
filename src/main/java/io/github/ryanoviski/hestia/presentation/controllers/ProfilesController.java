package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.services.ProfileService;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.domain.enums.DocumentType;
import javafx.stage.FileChooser;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Profile;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
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
        Button documents = new Button("Documentos");
        documents.getStyleClass().add("secondary-button");
        documents.setOnAction(event -> addDocument(profile));
        row.getChildren().add(documents);
        return row;
    }

    private void addDocument(Profile profile) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Adicionar documento a " + profile.name());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF e imagens", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
        var file = chooser.showOpenDialog(profilesList.getScene().getWindow());
        if (file == null) return;
        try {
            context.attachmentService().importForProfile(profile.id(), DocumentType.OTHER, null, file.toPath());
            showMessage("Documento adicionado ao perfil.", false);
        } catch (RuntimeException exception) {
            showMessage(exception.getMessage(), true);
        }
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
