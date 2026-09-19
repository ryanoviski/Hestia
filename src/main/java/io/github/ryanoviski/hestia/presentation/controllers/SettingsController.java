package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.services.BackupPreferencesService;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Arrays;

public final class SettingsController {
    @FXML private CheckBox automaticEnabled;
    @FXML private TextField automaticDirectory;
    @FXML private Spinner<Integer> retention;
    @FXML private Label status;
    @FXML private ProgressIndicator progress;
    private ApplicationContext context;

    public void configure(ApplicationContext context) {
        this.context = context;
        retention.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 10));
        var settings = context.backupPreferencesService().load();
        automaticEnabled.setSelected(settings.enabled());
        automaticDirectory.setText(settings.directory());
        retention.getValueFactory().setValue(settings.retention());
        status.setText("Último backup: " + (settings.lastBackup() == null ? "nunca" : settings.lastBackup()) + " · " + settings.lastResult());
    }

    @FXML private void createBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar backup do Hestia");
        chooser.setInitialFileName("hestia-backup.hestia-backup");
        File file = chooser.showSaveDialog(status.getScene().getWindow());
        if (file == null) return;

        CheckBox protect = new CheckBox("Proteger este backup com senha");
        PasswordField password = new PasswordField();
        PasswordField confirmation = new PasswordField();
        Label passwordLabel = new Label("Senha");
        Label confirmationLabel = new Label("Confirmar senha");
        Label hint = new Label("A senha usa proteção AES-256 e não será armazenada pelo Hestia.");
        hint.setWrapText(true);
        hint.getStyleClass().add("field-hint");
        VBox content = new VBox(9, protect, passwordLabel, password, confirmationLabel, confirmation, hint);
        password.disableProperty().bind(protect.selectedProperty().not());
        confirmation.disableProperty().bind(protect.selectedProperty().not());

        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Criar backup");
        dialog.setHeaderText("Defina a proteção do arquivo");
        dialog.getDialogPane().setContent(content);
        ThemeManager.apply(dialog, 540, 420);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        if (protect.isSelected() && (!password.getText().equals(confirmation.getText()) || password.getText().isBlank())) {
            message("Informe duas senhas iguais para proteger o backup.", true);
            return;
        }
        char[] secret = protect.isSelected() ? password.getText().toCharArray() : null;
        busy(true);
        context.backupService().createAsync(file.toPath(), secret).whenComplete((path, error) -> {
            if (secret != null) Arrays.fill(secret, '\0');
            Platform.runLater(() -> {
                busy(false);
                message(error == null ? "Backup criado em " + path : "Não foi possível criar o backup.", error != null);
            });
        });
    }

    @FXML private void restoreBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar backup do Hestia");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Backup Hestia", "*.hestia-backup"));
        File file = chooser.showOpenDialog(status.getScene().getWindow());
        if (file == null) return;
        PasswordField password = new PasswordField();
        password.setPromptText("Deixe em branco se o arquivo não tiver senha");
        Label warning = new Label("Os dados atuais serão substituídos somente após a validação completa. Um backup de segurança será criado automaticamente.");
        warning.setWrapText(true);
        warning.getStyleClass().add("origin-notice");
        VBox content = new VBox(10, warning, new Label("Senha do arquivo (opcional)"), password);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Restaurar backup");
        confirmation.setHeaderText("Confirme a restauração dos dados");
        confirmation.getDialogPane().setContent(content);
        ThemeManager.apply(confirmation, 570, 390);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        char[] secret = password.getText().isBlank() ? null : password.getText().toCharArray();
        busy(true);
        java.util.concurrent.CompletableFuture.runAsync(() -> context.backupService().restore(file.toPath(), secret))
                .whenComplete((ignored, error) -> {
                    if (secret != null) Arrays.fill(secret, '\0');
                    Platform.runLater(() -> {
                        busy(false);
                        message(error == null ? "Restauração concluída. Reinicie o Hestia."
                                : "O backup não foi restaurado. Os dados anteriores foram preservados.", error != null);
                    });
                });
    }

    @FXML private void saveAutomatic() {
        if (automaticEnabled.isSelected()) {
            Alert warning = new Alert(Alert.AlertType.CONFIRMATION,
                    "Backups automáticos não usam senha nesta versão. Deseja ativá-los mesmo assim?",
                    ButtonType.CANCEL, ButtonType.OK);
            warning.setHeaderText("Ativar backup automático");
            ThemeManager.apply(warning);
            if (warning.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        context.backupPreferencesService().save(new BackupPreferencesService.Settings(automaticEnabled.isSelected(),
                automaticDirectory.getText().trim(), retention.getValue(), null, "Configuração salva"));
        message("Configuração automática salva.", false);
    }

    private void busy(boolean value) { progress.setVisible(value); progress.setManaged(value); }

    private void message(String text, boolean error) {
        status.setText(text);
        status.getStyleClass().removeAll("form-error", "form-success");
        status.getStyleClass().add(error ? "form-error" : "form-success");
    }
}
