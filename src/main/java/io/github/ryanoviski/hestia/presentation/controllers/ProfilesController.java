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
import io.github.ryanoviski.hestia.presentation.components.DialogSupport;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
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
    @FXML private VBox emptyState;
    @FXML private Label formMessage;
    @FXML private Label profileCount;

    private ProfileService service;
    private ApplicationContext context;

    public void configure(ApplicationContext context) {
        this.context = context;
        this.service = context.profileService();
        refresh();
    }

    @FXML private void createProfile() { showProfileDialog(null); }

    private void edit(Profile profile) { showProfileDialog(profile); }

    private void showProfileDialog(Profile profile) {
        boolean creating = profile == null;
        TextField nameField = new TextField(creating ? "" : profile.name());
        nameField.setPromptText("Ex.: Ryan");
        ComboBox<ProfileType> typeField = new ComboBox<>();
        ComboBoxSupport.configure(typeField, ProfileType::displayName);
        typeField.getItems().setAll(ProfileType.values());
        typeField.setValue(creating ? ProfileType.PERSON : profile.type());
        typeField.setMaxWidth(Double.MAX_VALUE);
        Label typeHelp = new Label();
        typeHelp.setWrapText(true);
        typeHelp.getStyleClass().add("field-hint");
        updateTypeHelp(typeHelp, typeField.getValue());
        typeField.valueProperty().addListener((observable, oldValue, newValue) -> updateTypeHelp(typeHelp, newValue));
        ColorPalette colorPalette = new ColorPalette();
        colorPalette.setSelectedColor(creating ? null : profile.color());
        colorPalette.setMaxWidth(Double.MAX_VALUE);
        Label message = new Label();
        message.setWrapText(true);
        message.getStyleClass().add("feedback-text");
        nameField.textProperty().addListener((observable, oldValue, newValue) ->
                nameField.getStyleClass().remove("field-invalid"));

        VBox fields = DialogSupport.section("Identificação",
                "Defina como este perfil será reconhecido nos lançamentos.",
                DialogSupport.field("Nome", nameField, true),
                DialogSupport.field("Tipo", typeField, true), typeHelp,
                DialogSupport.field("Cor de identificação", colorPalette, false), message);

        Dialog<ButtonType> dialog = new Dialog<>();
        String title = creating ? "Novo perfil" : "Editar perfil";
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        ButtonType saveType = DialogSupport.primaryAction(creating ? "Cadastrar perfil" : "Salvar alterações");
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, saveType);
        dialog.getDialogPane().setContent(DialogSupport.content(
                creating ? "Adicione uma pessoa ou um perfil compartilhado."
                        : "Atualize os dados do perfil selecionado.", fields));
        DialogSupport.prepare(dialog, profilesList, 540, 0);

        boolean[] saved = {false};
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (creating) service.createProfile(nameField.getText(), typeField.getValue(), colorPalette.getSelectedColor());
                else service.updateProfile(profile.id(), nameField.getText(), typeField.getValue(), colorPalette.getSelectedColor());
                saved[0] = true;
            } catch (ValidationException exception) {
                event.consume();
                if (nameField.getText() == null || nameField.getText().isBlank()) {
                    if (!nameField.getStyleClass().contains("field-invalid")) nameField.getStyleClass().add("field-invalid");
                    nameField.requestFocus();
                }
                dialogMessage(message, exception.getMessage());
            } catch (RuntimeException exception) {
                event.consume();
                LOGGER.error("Could not save profile", exception);
                dialogMessage(message, "Não foi possível salvar o perfil. Tente novamente.");
            }
        });
        dialog.setOnShown(event -> nameField.requestFocus());
        dialog.showAndWait();
        if (saved[0]) {
            showMessage(creating ? "Perfil cadastrado com sucesso." : "Perfil atualizado com sucesso.", false);
            refresh();
        }
    }

    private void dialogMessage(Label message, String text) {
        message.setText(text == null ? "Não foi possível concluir a operação." : text);
        message.getStyleClass().removeAll("form-error", "form-success");
        message.getStyleClass().add("form-error");
    }

    private void refresh() {
        try {
            var profiles = service.listProfiles();
            long active = profiles.stream().filter(Profile::active).count();
            profileCount.setText(active == 1 ? "1 ativo" : active + " ativos");
            profilesList.getChildren().clear();
            profiles.forEach(profile -> profilesList.getChildren().add(createRow(profile)));
            emptyState.setVisible(profiles.isEmpty());
            emptyState.setManaged(profiles.isEmpty());
            if (profiles.isEmpty()) profilesList.getChildren().add(emptyState);
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
        Label details = new Label(profile.type() == ProfileType.PERSON
                ? "Pessoa · " + (profile.active() ? "Ativo" : "Inativo")
                : "Compartilhado · " + (profile.active() ? "Ativo" : "Inativo"));
        details.getStyleClass().add("profile-meta");
        VBox identity = new VBox(3, name, details);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        MenuButton actions = new MenuButton("Ações");
        actions.getStyleClass().add("ghost-button");
        actions.getItems().add(menu("Editar", () -> edit(profile)));
        actions.getItems().add(menu("Anexos", () -> manageAttachments(profile)));
        actions.getItems().add(menu(profile.active() ? "Desativar" : "Reativar", () -> toggle(profile)));
        actions.getItems().add(menu("Excluir", () -> delete(profile)));
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

    private MenuItem menu(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private void updateTypeHelp(Label typeHelp, ProfileType type) {
        typeHelp.setText(type == ProfileType.SHARED
                ? "Use para despesas e receitas da família, casal ou grupo."
                : "Use para movimentações relacionadas a uma pessoa.");
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
        if (!DialogSupport.confirm(profilesList, "Excluir perfil", "Excluir “" + profile.name() + "”?",
                "A exclusão só será permitida quando não houver histórico ou anexos vinculados.",
                "Excluir perfil", true)) return;
        try {
            service.deleteProfile(profile.id());
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
        dialog.setHeaderText("Anexos do perfil");
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
        VBox filesList = new VBox(8); filesList.getStyleClass().add("attachment-list");
        Button add = new Button("Adicionar anexo");
        add.getStyleClass().add("primary-button");

        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            var attachments = context.attachmentService().search(new AttachmentFilter(
                    null, profile.id(), null, null, null, null, false, null));
            filesList.getChildren().clear();
            if (attachments.isEmpty()) {
                Label empty = new Label("Nenhum anexo neste perfil."); empty.getStyleClass().add("empty-state");
                filesList.getChildren().add(empty);
            }
            attachments.forEach(attachment -> {
                Label label = new Label(attachment.originalFilename()); label.getStyleClass().add("attachment-name");
                Label metadata = new Label(attachment.fileExtension().toUpperCase() + " · "
                        + formatSize(attachment.sizeBytes()) + " · " + attachment.documentType().displayName()
                        + " · " + attachment.integrity().displayName());
                metadata.getStyleClass().add("attachment-meta");
                VBox identity = new VBox(2, label, metadata);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Button open = action("Abrir", "table-action", () -> context.attachmentService().openExternal(attachment.id())
                        .exceptionally(error -> { Platform.runLater(() -> showMessage("Não foi possível abrir o anexo.", true)); return null; }));
                Button remove = action("Remover", "destructive-button", () -> {
                    if (DialogSupport.confirm(filesList, "Remover anexo",
                            "Remover “" + attachment.originalFilename() + "”?",
                            "O arquivo será removido, mas o perfil será preservado.", "Remover", true)) {
                        context.attachmentService().remove(attachment.id());
                        load[0].run();
                    }
                });
                HBox attachmentRow = new HBox(8, identity, spacer, open, remove);
                attachmentRow.setAlignment(Pos.CENTER_LEFT);
                attachmentRow.getStyleClass().add("attachment-row");
                filesList.getChildren().add(attachmentRow);
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

        load[0].run();
        VBox content = DialogSupport.content("Arquivos pessoais ficam armazenados somente neste computador.",
                add, DialogSupport.scrollRegion(filesList, 300));
        dialog.getDialogPane().setContent(content);
        DialogSupport.prepare(dialog, profilesList, 700, 500);
        dialog.showAndWait();
    }

    private void showMessage(String message, boolean error) {
        formMessage.setText(message == null ? "Não foi possível concluir a operação." : message);
        formMessage.getStyleClass().removeAll("form-error", "form-success");
        formMessage.getStyleClass().add(error ? "form-error" : "form-success");
    }

    private String formatSize(long size) {
        return size < 1024 ? size + " B" : size < 1024 * 1024
                ? String.format("%.1f KB", size / 1024d) : String.format("%.1f MB", size / 1024d / 1024d);
    }
}
