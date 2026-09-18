package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.dto.TransactionFilter;
import io.github.ryanoviski.hestia.application.dto.TransactionInput;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.domain.models.Transaction;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class TransactionsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionsController.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    @FXML private Label heading;
    @FXML private TextField searchField;
    @FXML private TextField monthField;
    @FXML private ComboBox<TransactionType> typeFilter;
    @FXML private ComboBox<TransactionStatus> statusFilter;
    @FXML private ComboBox<Profile> profileFilter;
    @FXML private ComboBox<Category> categoryFilter;
    @FXML private CheckBox overdueOnly;
    @FXML private VBox transactionList;
    @FXML private Label emptyState;
    @FXML private Label resultCount;

    private ApplicationContext context;
    private TransactionType fixedType;
    private boolean payableMode;

    public void configure(ApplicationContext context, TransactionType fixedType, boolean payableMode) {
        this.context = context; this.fixedType = fixedType; this.payableMode = payableMode;
        heading.setText(payableMode ? "Contas a pagar" : fixedType == TransactionType.INCOME ? "Receitas" : "Movimentações");
        monthField.setText(YearMonth.now().format(DateTimeFormatter.ofPattern("MM/yyyy")));
        typeFilter.getItems().setAll(TransactionType.values()); typeFilter.setValue(fixedType); typeFilter.setDisable(fixedType != null);
        statusFilter.getItems().setAll(TransactionStatus.values());
        configureStatusLabels(statusFilter, () -> fixedType);
        if (payableMode) statusFilter.setValue(TransactionStatus.PENDING);
        profileFilter.getItems().setAll(context.profileService().listProfiles());
        loadCategoryFilter();
        refresh();
    }

    @FXML private void applyFilters() { loadCategoryFilter(); refresh(); }
    @FXML private void clearFilters() {
        searchField.clear(); monthField.setText(YearMonth.now().format(DateTimeFormatter.ofPattern("MM/yyyy")));
        typeFilter.setValue(fixedType); statusFilter.setValue(payableMode ? TransactionStatus.PENDING : null);
        profileFilter.setValue(null); categoryFilter.setValue(null); overdueOnly.setSelected(false); refresh();
    }
    @FXML private void newTransaction() { showForm(null); }

    private void refresh() {
        if (context == null) return;
        try {
            YearMonth month = parseMonth();
            TransactionType type = fixedType == null ? typeFilter.getValue() : fixedType;
            var filter = new TransactionFilter(searchField.getText(), month, type, statusFilter.getValue(),
                    id(profileFilter.getValue()), id(categoryFilter.getValue()), overdueOnly.isSelected(), payableMode);
            var items = context.transactionService().search(filter);
            transactionList.getChildren().clear(); items.forEach(item -> transactionList.getChildren().add(row(item)));
            emptyState.setVisible(items.isEmpty()); emptyState.setManaged(items.isEmpty());
            resultCount.setText(items.size() + (items.size() == 1 ? " movimentação" : " movimentações"));
        } catch (ValidationException exception) { showError(exception.getMessage()); }
        catch (RuntimeException exception) { LOGGER.error("Could not load transactions", exception); showError("Não foi possível carregar as movimentações."); }
    }

    private HBox row(Transaction item) {
        Label description = new Label(item.description()); description.getStyleClass().add("transaction-description");
        String meta = DATE.format(item.referenceDate()) + " · " + item.profileName() + " · " + item.categoryName();
        if (item.dueDate() != null) meta += " · Vencimento " + DATE.format(item.dueDate());
        Label details = new Label(meta); details.getStyleClass().add("profile-meta");
        VBox identity = new VBox(4, description, details);
        Label status = new Label(item.isOverdue(Clock.systemDefaultZone()) ? "Vencida" : item.status().displayName(item.type()));
        status.getStyleClass().addAll("status-pill", "status-" + (item.isOverdue(Clock.systemDefaultZone()) ? "overdue" : item.status().name().toLowerCase()));
        Label amount = new Label(MoneyUtils.formatCents(item.amountCents())); amount.getStyleClass().add("transaction-amount");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button view = action("Ver", () -> view(item)); Button edit = action("Editar", () -> showForm(item));
        Button settle = action(item.status() == TransactionStatus.SETTLED ? "Reabrir" : item.type() == TransactionType.INCOME ? "Receber" : "Pagar",
                () -> changeSettlement(item));
        Button cancel = action("Cancelar", () -> cancel(item));
        HBox actions = new HBox(6, view, edit);
        if (item.status() != TransactionStatus.CANCELLED) actions.getChildren().add(settle);
        if (item.status() != TransactionStatus.CANCELLED) actions.getChildren().add(cancel);
        HBox row = new HBox(12, identity, spacer, status, amount, actions); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("transaction-row");
        if (item.status() == TransactionStatus.CANCELLED) row.getStyleClass().add("transaction-cancelled");
        if (item.isOverdue(Clock.systemDefaultZone())) row.getStyleClass().add("transaction-overdue");
        return row;
    }

    private Button action(String text, Runnable runnable) { Button button = new Button(text); button.getStyleClass().add("table-action"); button.setOnAction(event -> runnable.run()); return button; }

    private void showForm(Transaction existing) {
        Dialog<Void> dialog = new Dialog<>(); dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(existing == null ? "Nova movimentação" : "Editar movimentação");
        ButtonType saveType = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        ComboBox<TransactionType> type = new ComboBox<>(); type.getItems().setAll(TransactionType.values()); type.setValue(existing == null ? (fixedType == null ? TransactionType.EXPENSE : fixedType) : existing.type()); type.setDisable(fixedType != null);
        TextField description = new TextField(existing == null ? "" : existing.description());
        TextField amount = new TextField(existing == null ? "" : MoneyUtils.fromCents(existing.amountCents()).toPlainString().replace('.', ','));
        ComboBox<Profile> profile = new ComboBox<>(); profile.getItems().setAll(context.profileService().listProfiles().stream().filter(p -> existing != null || p.active()).toList()); selectProfile(profile, existing);
        ComboBox<Category> category = new ComboBox<>();
        DatePicker reference = new DatePicker(existing == null ? LocalDate.now() : existing.referenceDate());
        DatePicker due = new DatePicker(existing == null ? null : existing.dueDate());
        ComboBox<TransactionStatus> status = new ComboBox<>(); status.getItems().setAll(TransactionStatus.values()); status.setValue(existing == null ? TransactionStatus.PENDING : existing.status());
        DatePicker settlement = new DatePicker(existing == null ? null : existing.settlementDate());
        configureStatusLabels(status, type::getValue);
        status.valueProperty().addListener((observable, oldStatus, newStatus) -> {
            if (newStatus == TransactionStatus.SETTLED && settlement.getValue() == null) settlement.setValue(LocalDate.now());
            if (newStatus != TransactionStatus.SETTLED) settlement.setValue(null);
            status.setButtonCell(status.getButtonCell());
        });
        TextArea notes = new TextArea(existing == null ? "" : existing.notes()); notes.setPrefRowCount(3); notes.setWrapText(true);
        Label error = new Label(); error.getStyleClass().add("form-error"); error.setWrapText(true);
        Runnable loadCategories = () -> { Category selected = category.getValue(); category.getItems().setAll(context.categoryService().search(type.getValue().categoryType(), null, existing != null)); if (existing != null) category.getItems().stream().filter(c -> c.id().equals(existing.categoryId())).findFirst().ifPresent(category::setValue); else if (selected != null && category.getItems().contains(selected)) category.setValue(selected); };
        type.valueProperty().addListener((o,a,b) -> loadCategories.run()); loadCategories.run();
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(9); grid.setPadding(new Insets(8));
        int r=0; add(grid,r++,"Tipo",type); add(grid,r++,"Descrição",description); add(grid,r++,"Valor",amount); add(grid,r++,"Perfil",profile); add(grid,r++,"Categoria",category); add(grid,r++,"Data de referência",reference); add(grid,r++,"Data prevista / vencimento",due); add(grid,r++,"Situação",status); add(grid,r++,"Data de conclusão",settlement); add(grid,r++,"Observações",notes); grid.add(error,1,r);
        dialog.getDialogPane().setContent(grid); dialog.getDialogPane().setPrefWidth(570);
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.disableProperty().bind(description.textProperty().isEmpty().or(amount.textProperty().isEmpty()).or(profile.valueProperty().isNull()).or(category.valueProperty().isNull()));
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try {
                TransactionInput input = new TransactionInput(type.getValue(), description.getText(), MoneyUtils.parseBrazilian(amount.getText()), profile.getValue().id(), category.getValue().id(), reference.getValue(), due.getValue(), status.getValue(), settlement.getValue(), notes.getText());
                if (existing == null) context.transactionService().create(input); else context.transactionService().update(existing.id(), input);
                dialog.close(); refresh();
            } catch (ValidationException exception) { error.setText(exception.getMessage()); }
            catch (RuntimeException exception) { LOGGER.error("Could not save transaction", exception); error.setText("Não foi possível salvar a movimentação."); }
        });
        dialog.showAndWait();
    }

    private void changeSettlement(Transaction item) {
        try { if (item.status() == TransactionStatus.SETTLED) context.transactionService().reopen(item.id()); else context.transactionService().settle(item.id(), LocalDate.now()); refresh(); }
        catch (RuntimeException exception) { LOGGER.error("Could not change transaction status", exception); showError("Não foi possível alterar a situação."); }
    }
    private void cancel(Transaction item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Cancelar \"" + item.description() + "\"? O registro continuará no histórico.", ButtonType.CANCEL, ButtonType.OK);
        confirm.setHeaderText("Cancelar movimentação");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try { context.transactionService().cancel(item.id()); refresh(); }
            catch (RuntimeException exception) { LOGGER.error("Could not cancel transaction", exception); showError("Não foi possível cancelar a movimentação."); }
        }
    }
    private void view(Transaction item) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION); alert.setHeaderText(item.description()); alert.setTitle("Movimentação");
        alert.setContentText("Tipo: " + item.type().displayName() + "\nSituação: " + item.status().displayName(item.type()) + "\nValor: " + MoneyUtils.formatCents(item.amountCents()) + "\nPerfil: " + item.profileName() + "\nCategoria: " + item.categoryName() + "\nReferência: " + DATE.format(item.referenceDate()) + (item.notes() == null ? "" : "\n\nObservações:\n" + item.notes())); alert.showAndWait();
    }
    private void loadCategoryFilter() { if(context==null)return; TransactionType type=fixedType==null?typeFilter.getValue():fixedType; Category current=categoryFilter.getValue(); categoryFilter.getItems().setAll(context.categoryService().search(type==null?null:type.categoryType(),null,false)); if(current!=null&&categoryFilter.getItems().contains(current))categoryFilter.setValue(current); }
    private YearMonth parseMonth() { if(monthField.getText()==null||monthField.getText().isBlank())return null; try{return YearMonth.parse(monthField.getText().trim(),DateTimeFormatter.ofPattern("MM/yyyy"));}catch(DateTimeParseException e){throw new ValidationException("Informe o mês no formato MM/AAAA.");} }
    private Long id(Profile p){return p==null?null:p.id();} private Long id(Category c){return c==null?null:c.id();}
    private void selectProfile(ComboBox<Profile> box,Transaction t){if(t!=null)box.getItems().stream().filter(p->p.id().equals(t.profileId())).findFirst().ifPresent(box::setValue);else box.getItems().stream().filter(Profile::active).findFirst().ifPresent(box::setValue);}
    private void add(GridPane grid,int row,String label,Control control){grid.add(new Label(label),0,row);control.setMaxWidth(Double.MAX_VALUE);grid.add(control,1,row);GridPane.setHgrow(control,Priority.ALWAYS);}
    private void configureStatusLabels(ComboBox<TransactionStatus> combo, java.util.function.Supplier<TransactionType> type) {
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(TransactionStatus status) {
                if (status == null) return "";
                TransactionType selected = type.get();
                return selected == null ? switch (status) { case PENDING -> "Pendente"; case SETTLED -> "Concluída"; case CANCELLED -> "Cancelada"; }
                        : status.displayName(selected);
            }
            @Override public TransactionStatus fromString(String string) { return null; }
        });
    }
    private void showError(String message){Alert alert=new Alert(Alert.AlertType.ERROR,message,ButtonType.OK);alert.setHeaderText("Não foi possível concluir a operação");alert.showAndWait();}
}
