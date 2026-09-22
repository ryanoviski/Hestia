package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.services.BackupPreferencesService;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.config.ApplicationPaths;
import io.github.ryanoviski.hestia.presentation.components.DialogSupport;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.Arrays;

public final class SettingsController {
    @FXML private CheckBox automaticEnabled;
    @FXML private TextField automaticDirectory;
    @FXML private Spinner<Integer> retention;
    @FXML private Label status;
    @FXML private ProgressIndicator progress;
    @FXML private Label localDataPath;
    private ApplicationContext context;

    public void configure(ApplicationContext context) {
        this.context = context;
        retention.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 10));
        var settings = context.backupPreferencesService().load();
        automaticEnabled.setSelected(settings.enabled());
        automaticDirectory.setText(settings.directory());
        retention.getValueFactory().setValue(settings.retention());
        status.setText("Último backup: " + (settings.lastBackup() == null ? "nunca" : settings.lastBackup()) + " · " + settings.lastResult());
        localDataPath.setText(ApplicationPaths.resolve().root().toString());
    }

    @FXML private void chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Escolher pasta para backups automáticos");
        if (!automaticDirectory.getText().isBlank()) {
            File current = new File(automaticDirectory.getText());
            if (current.isDirectory()) chooser.setInitialDirectory(current);
        }
        File selected = chooser.showDialog(status.getScene().getWindow());
        if (selected != null) automaticDirectory.setText(selected.getAbsolutePath());
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
        password.disableProperty().bind(protect.selectedProperty().not());
        confirmation.disableProperty().bind(protect.selectedProperty().not());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Criar backup");
        dialog.setHeaderText("Proteção do backup");
        ButtonType createType = DialogSupport.primaryAction("Criar backup");
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, createType);
        VBox passwordFields = DialogSupport.section("Senha opcional",
                "A proteção usa AES-256. A senha não é armazenada pelo Hestia.",
                protect, DialogSupport.field("Senha", password, false),
                DialogSupport.field("Confirmar senha", confirmation, false));
        VBox content = DialogSupport.content("Escolha se esta cópia deve exigir senha para restauração.", passwordFields);
        dialog.getDialogPane().setContent(content);
        DialogSupport.prepare(dialog, status, 540, 0);
        if (dialog.showAndWait().orElse(cancelType) != createType) return;
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
        Dialog<ButtonType> confirmation = new Dialog<>();
        confirmation.setTitle("Restaurar backup");
        confirmation.setHeaderText("Restaurar os dados do Hestia");
        ButtonType restoreType = DialogSupport.destructiveAction("Restaurar dados");
        ButtonType cancelType = new ButtonType("Voltar", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmation.getDialogPane().getButtonTypes().addAll(cancelType, restoreType);
        VBox restoreSection = DialogSupport.section("Antes de continuar",
                "Os dados atuais serão substituídos somente após a validação completa. Uma cópia de segurança será criada automaticamente.",
                DialogSupport.field("Senha do arquivo (opcional)", password, false));
        VBox content = DialogSupport.content("O Hestia verificará o arquivo antes de modificar seus dados.", restoreSection);
        confirmation.getDialogPane().setContent(content);
        DialogSupport.prepare(confirmation, status, 570, 0);
        DialogSupport.markDestructive(confirmation, restoreType);
        if (confirmation.showAndWait().orElse(cancelType) != restoreType) return;
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
            if (!DialogSupport.confirm(status, "Backup automático", "Ativar backup automático?",
                    "Backups automáticos não usam senha nesta versão.", "Ativar", false)) return;
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
