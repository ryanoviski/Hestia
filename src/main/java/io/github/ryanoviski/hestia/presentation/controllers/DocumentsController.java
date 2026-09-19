package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.dto.AttachmentFilter;
import io.github.ryanoviski.hestia.application.dto.StorageDiagnostic;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.domain.enums.AttachmentIntegrity;
import io.github.ryanoviski.hestia.domain.enums.DocumentType;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.domain.models.Attachment;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.presentation.components.ComboBoxSupport;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class DocumentsController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    @FXML private TextField nameFilter;
    @FXML private ComboBox<Profile> profileFilter;
    @FXML private ComboBox<DocumentType> typeFilter;
    @FXML private ComboBox<TransactionType> transactionTypeFilter;
    @FXML private ComboBox<String> originFilter;
    @FXML private ComboBox<AttachmentIntegrity> integrityFilter;
    @FXML private DatePicker fromFilter;
    @FXML private DatePicker toFilter;
    @FXML private VBox documentsList;
    @FXML private Label emptyState;
    private ApplicationContext context;
    private final AtomicLong renderToken = new AtomicLong();

    public void configure(ApplicationContext context) {
        this.context = context;
        ComboBoxSupport.profiles(profileFilter);
        ComboBoxSupport.configure(typeFilter, DocumentType::displayName);
        ComboBoxSupport.configure(transactionTypeFilter, TransactionType::displayName);
        ComboBoxSupport.configure(integrityFilter, AttachmentIntegrity::displayName);
        profileFilter.getItems().setAll(context.profileService().listProfiles());
        typeFilter.getItems().setAll(DocumentType.values());
        transactionTypeFilter.getItems().setAll(TransactionType.values());
        integrityFilter.getItems().setAll(AttachmentIntegrity.values());
        originFilter.getItems().setAll("Movimentação", "Perfil");
        refresh();
    }

    @FXML private void refresh() {
        var filter = new AttachmentFilter(nameFilter.getText(), id(profileFilter.getValue()), typeFilter.getValue(),
                transactionTypeFilter.getValue(), fromFilter.getValue(), toFilter.getValue(),
                originFilter.getValue() == null ? null : originFilter.getValue().equals("Movimentação"), integrityFilter.getValue());
        var items = context.attachmentService().search(filter);
        documentsList.getChildren().clear();
        items.forEach(attachment -> documentsList.getChildren().add(row(attachment)));
        emptyState.setVisible(items.isEmpty());
        emptyState.setManaged(items.isEmpty());
        if (items.isEmpty()) documentsList.getChildren().add(emptyState);
    }

    @FXML private void addProfileDocument() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Adicionar documento ao perfil");
        dialog.setHeaderText("Escolha o perfil e a identificação do arquivo");
        ComboBox<Profile> profile = new ComboBox<>();
        profile.getItems().setAll(context.profileService().listProfiles());
        ComboBoxSupport.profiles(profile);
        ComboBox<DocumentType> type = new ComboBox<>();
        type.getItems().setAll(DocumentType.values());
        ComboBoxSupport.configure(type, DocumentType::displayName);
        type.setValue(DocumentType.OTHER);
        TextField description = new TextField();
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(8));
        add(grid, 0, "Perfil *", profile);
        add(grid, 1, "Tipo *", type);
        add(grid, 2, "Descrição", description);
        dialog.getDialogPane().setContent(grid);
        ButtonType next = new ButtonType("Selecionar arquivo", ButtonBar.ButtonData.NEXT_FORWARD);
        dialog.getDialogPane().getButtonTypes().addAll(next, ButtonType.CANCEL);
        ThemeManager.apply(dialog, 580, 390);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != next || profile.getValue() == null) return;
        File file = chooser("Selecionar documento").showOpenDialog(documentsList.getScene().getWindow());
        if (file != null) {
            try {
                context.attachmentService().importForProfile(profile.getValue().id(), type.getValue(), description.getText(), file.toPath());
                refresh();
            } catch (RuntimeException exception) {
                error(exception.getMessage());
            }
        }
    }

    @FXML private void diagnose() {
        StorageDiagnostic diagnostic = context.attachmentService().diagnose();
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Registros sem arquivo: " + diagnostic.missingRecords().size()
                        + "\nArquivos sem registro: " + diagnostic.orphanFiles().size()
                        + "\nTemporários abandonados: " + diagnostic.temporaryFiles().size(), ButtonType.OK);
        alert.setHeaderText("Diagnóstico dos anexos");
        ThemeManager.apply(alert);
        alert.showAndWait();
    }

    private HBox row(Attachment attachment) {
        Label name = new Label(attachment.originalFilename());
        name.getStyleClass().add("transaction-description");
        Label details = new Label(attachment.documentType().displayName() + " · " + formatSize(attachment.sizeBytes())
                + " · " + attachment.fileExtension().toUpperCase() + " · "
                + DATE.format(attachment.createdAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()));
        details.getStyleClass().add("profile-meta");
        Label source = new Label((attachment.profileName() == null ? "" : attachment.profileName())
                + (attachment.transactionDescription() == null ? "" : " · " + attachment.transactionDescription()));
        source.setWrapText(true);
        VBox info = new VBox(3, name, details, source);
        Label integrity = new Label(attachment.integrity().displayName());
        integrity.getStyleClass().addAll("status-pill",
                attachment.integrity() == AttachmentIntegrity.AVAILABLE ? "status-settled" : "status-overdue");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        MenuButton actions = new MenuButton("Ações");
        actions.getStyleClass().add("secondary-button");
        actions.getItems().add(menu("Visualizar", () -> preview(attachment)));
        actions.getItems().add(menu("Abrir externamente", () -> context.attachmentService().openExternal(attachment.id())
                .exceptionally(exception -> { Platform.runLater(() -> error(exception.getMessage())); return null; })));
        actions.getItems().add(menu("Exportar", () -> export(attachment)));
        actions.getItems().add(menu("Verificar integridade", () -> { context.attachmentService().verify(attachment.id()); refresh(); }));
        actions.getItems().add(menu("Remover", () -> remove(attachment)));
        HBox row = new HBox(8, info, spacer, integrity, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("transaction-row");
        return row;
    }

    private void preview(Attachment attachment) {
        if (attachment.integrity() != AttachmentIntegrity.AVAILABLE) {
            error("O arquivo está ausente, alterado ou inválido.");
            return;
        }
        Stage stage = new Stage();
        ImageView view = new ImageView();
        view.setPreserveRatio(true);
        view.setFitWidth(850);
        view.setFitHeight(620);
        Label status = new Label("Carregando...");
        Button previous = new Button("Anterior");
        Button next = new Button("Próxima");
        Button minus = new Button("Diminuir");
        Button plus = new Button("Ampliar");
        HBox controls = new HBox(8, previous, next, minus, plus, status);
        controls.setPadding(new Insets(10));
        controls.setAlignment(Pos.CENTER);
        BorderPane pane = new BorderPane(new ScrollPane(view), controls, null, null, null);
        stage.setScene(new Scene(pane, 900, 700));
        stage.setMinWidth(680);
        stage.setMinHeight(520);
        stage.setTitle(attachment.originalFilename());
        ThemeManager.apply(stage);
        stage.show();
        int[] page = {0};
        int[] pages = {1};
        float[] dpi = {110};
        Runnable render = () -> {
            long token = renderToken.incrementAndGet();
            status.setText("Carregando...");
            CompletableFuture.supplyAsync(() -> attachment.mediaType().equals("application/pdf")
                    ? context.documentPreviewService().renderPdf(context.attachmentService().file(attachment), page[0], dpi[0])
                    : context.documentPreviewService().loadImage(context.attachmentService().file(attachment), 1600, 1200))
                    .thenAccept(image -> Platform.runLater(() -> {
                        if (token == renderToken.get()) {
                            view.setImage(SwingFXUtils.toFXImage(image, null));
                            status.setText(attachment.mediaType().equals("application/pdf")
                                    ? "Página " + (page[0] + 1) + " de " + pages[0] : formatSize(attachment.sizeBytes()));
                        }
                    })).exceptionally(exception -> { Platform.runLater(() -> status.setText("Não foi possível visualizar.")); return null; });
        };
        if (attachment.mediaType().equals("application/pdf")) {
            pages[0] = context.documentPreviewService().pdfPageCount(context.attachmentService().file(attachment));
        } else {
            previous.setDisable(true);
            next.setDisable(true);
        }
        previous.setOnAction(event -> { if (page[0] > 0) { page[0]--; render.run(); } });
        next.setOnAction(event -> { if (page[0] + 1 < pages[0]) { page[0]++; render.run(); } });
        minus.setOnAction(event -> { dpi[0] = Math.max(55, dpi[0] - 20); render.run(); });
        plus.setOnAction(event -> { dpi[0] = Math.min(220, dpi[0] + 20); render.run(); });
        render.run();
    }

    private void export(Attachment attachment) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(attachment.originalFilename());
        File file = chooser.showSaveDialog(documentsList.getScene().getWindow());
        if (file == null || file.exists() && !confirm("Substituir arquivo", "Já existe um arquivo com esse nome. Deseja substituí-lo?")) return;
        try {
            context.attachmentService().export(attachment.id(), file.toPath(), true);
        } catch (RuntimeException exception) {
            error(exception.getMessage());
        }
    }

    private void remove(Attachment attachment) {
        if (!confirm("Remover anexo", "Remover \"" + attachment.originalFilename() + "\"? A movimentação ou o perfil será preservado.")) return;
        try {
            context.attachmentService().remove(attachment.id());
            refresh();
        } catch (RuntimeException exception) {
            error(exception.getMessage());
        }
    }

    private boolean confirm(String header, String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.CANCEL, ButtonType.OK);
        alert.setHeaderText(header);
        ThemeManager.apply(alert);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void error(String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                text == null ? "Não foi possível concluir a operação." : text, ButtonType.OK);
        alert.setHeaderText("Não foi possível concluir a operação");
        ThemeManager.apply(alert);
        alert.showAndWait();
    }

    private void add(GridPane grid, int row, String text, Control control) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        label.setMinWidth(120);
        grid.add(label, 0, row);
        control.setMaxWidth(Double.MAX_VALUE);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private MenuItem menu(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF e imagens", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
        return chooser;
    }

    private Long id(Profile profile) { return profile == null ? null : profile.id(); }

    private String formatSize(long size) {
        return size < 1024 ? size + " B" : size < 1024 * 1024
                ? String.format("%.1f KB", size / 1024d) : String.format("%.1f MB", size / 1024d / 1024d);
    }
}
