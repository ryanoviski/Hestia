package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.dto.AttachmentFilter;
import io.github.ryanoviski.hestia.application.dto.InstallmentPlanInput;
import io.github.ryanoviski.hestia.application.dto.RecurringExpenseInput;
import io.github.ryanoviski.hestia.application.dto.TransactionFilter;
import io.github.ryanoviski.hestia.application.dto.TransactionInput;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.enums.DocumentType;
import io.github.ryanoviski.hestia.domain.enums.TransactionOrigin;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.domain.models.Transaction;
import io.github.ryanoviski.hestia.presentation.components.ComboBoxSupport;
import io.github.ryanoviski.hestia.presentation.components.MonthYearPicker;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class TransactionsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionsController.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label heading;
    @FXML private Label subtitle;
    @FXML private Button newButton;
    @FXML private Button recurringManagerButton;
    @FXML private Button installmentManagerButton;
    @FXML private TextField searchField;
    @FXML private MonthYearPicker monthPicker;
    @FXML private Button filterToggle;
    @FXML private FlowPane advancedFilters;
    @FXML private ComboBox<TransactionType> typeFilter;
    @FXML private ComboBox<TransactionStatus> statusFilter;
    @FXML private ComboBox<Profile> profileFilter;
    @FXML private ComboBox<Category> categoryFilter;
    @FXML private ComboBox<TransactionOrigin> originFilter;
    @FXML private CheckBox overdueOnly;
    @FXML private VBox transactionList;
    @FXML private VBox emptyState;
    @FXML private Label emptyTitle;
    @FXML private Label emptyDescription;
    @FXML private Button emptyAction;
    @FXML private Label resultCount;
    @FXML private Label feedback;

    private ApplicationContext context;
    private TransactionType fixedType;
    private boolean payableMode;

    public void configure(ApplicationContext context, TransactionType fixedType, boolean payableMode) {
        this.context = context;
        this.fixedType = fixedType;
        this.payableMode = payableMode;
        configureCopy();
        monthPicker.setValue(java.time.YearMonth.now());
        typeFilter.getItems().setAll(TransactionType.values());
        typeFilter.setValue(fixedType);
        typeFilter.setDisable(fixedType != null);
        statusFilter.getItems().setAll(TransactionStatus.values());
        originFilter.getItems().setAll(TransactionOrigin.values());
        ComboBoxSupport.configure(typeFilter, TransactionType::displayName);
        ComboBoxSupport.configure(originFilter, TransactionOrigin::displayName);
        configureStatusLabels(statusFilter, () -> fixedType);
        if (payableMode) statusFilter.setValue(TransactionStatus.PENDING);
        profileFilter.getItems().setAll(context.profileService().listProfiles());
        ComboBoxSupport.profiles(profileFilter);
        ComboBoxSupport.categories(categoryFilter);
        loadCategoryFilter();
        searchField.setOnAction(event -> refresh());
        monthPicker.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        refresh();
    }

    private void configureCopy() {
        if (payableMode) {
            heading.setText("Contas a pagar");
            subtitle.setText("Registre uma conta única, recorrente ou parcelada no mesmo fluxo.");
            newButton.setText("Nova conta");
            emptyTitle.setText("Nenhuma conta pendente");
            emptyDescription.setText("Você não possui contas para pagar neste período.");
            emptyAction.setText("Registrar conta");
            recurringManagerButton.setVisible(true); recurringManagerButton.setManaged(true);
            installmentManagerButton.setVisible(true); installmentManagerButton.setManaged(true);
        } else if (fixedType == TransactionType.INCOME) {
            heading.setText("Receitas");
            subtitle.setText("Acompanhe o que foi recebido e o que ainda está previsto.");
            newButton.setText("Nova receita");
            emptyTitle.setText("Nenhuma receita neste período");
            emptyDescription.setText("Registre uma receita prevista ou já recebida.");
            emptyAction.setText("Registrar receita");
        } else {
            heading.setText("Movimentações");
            subtitle.setText("Consulte seu histórico financeiro sem perder o contexto de cada lançamento.");
        }
    }

    @FXML private void toggleFilters() {
        boolean show = !advancedFilters.isVisible();
        advancedFilters.setVisible(show);
        advancedFilters.setManaged(show);
        filterToggle.setText(show ? "Ocultar filtros" : "Filtros");
    }

    @FXML private void applyFilters() { loadCategoryFilter(); refresh(); }

    @FXML private void clearFilters() {
        searchField.clear();
        monthPicker.setValue(java.time.YearMonth.now());
        typeFilter.setValue(fixedType);
        statusFilter.setValue(payableMode ? TransactionStatus.PENDING : null);
        profileFilter.setValue(null);
        categoryFilter.setValue(null);
        originFilter.setValue(null);
        overdueOnly.setSelected(false);
        refresh();
    }

    @FXML private void newTransaction() {
        if (payableMode) showAccountForm();
        else showTransactionForm(null);
    }

    @FXML private void manageRecurring() { showCommitmentManager(true); }
    @FXML private void manageInstallments() { showCommitmentManager(false); }

    private void refresh() {
        if (context == null) return;
        try {
            TransactionType type = fixedType == null ? typeFilter.getValue() : fixedType;
            var filter = new TransactionFilter(searchField.getText(), monthPicker.getValue(), type,
                    statusFilter.getValue(), id(profileFilter.getValue()), id(categoryFilter.getValue()),
                    overdueOnly.isSelected(), payableMode, originFilter.getValue());
            var items = context.transactionService().search(filter);
            transactionList.getChildren().clear();
            items.forEach(item -> transactionList.getChildren().add(row(item)));
            toggle(emptyState, items.isEmpty());
            if (items.isEmpty()) transactionList.getChildren().add(emptyState);
            resultCount.setText(items.size() + (items.size() == 1 ? " resultado" : " resultados"));
        } catch (ValidationException exception) {
            showError(exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not load transactions", exception);
            showError("Não foi possível carregar as movimentações.");
        }
    }

    private HBox row(Transaction item) {
        boolean overdue = item.isOverdue(Clock.systemDefaultZone());
        Label marker = new Label(item.type() == TransactionType.INCOME ? "R" : "D");
        marker.setAccessibleText(item.type().displayName());
        marker.getStyleClass().addAll("type-marker", item.type() == TransactionType.INCOME ? "type-income" : "type-expense");
        Label description = new Label(item.description());
        description.getStyleClass().add("transaction-description");
        Label kind = new Label(item.type().displayName() + " · " + item.categoryName());
        kind.getStyleClass().add("section-caption");
        VBox identity = new VBox(2, description, kind);
        identity.setMinWidth(170);
        Label date = new Label(DATE.format(item.dueDate() == null ? item.referenceDate() : item.dueDate()));
        date.getStyleClass().add("transaction-date");
        Label profile = new Label(item.profileName());
        profile.getStyleClass().add("transaction-profile");
        Label origin = new Label(originText(item));
        origin.getStyleClass().add("origin-pill");
        VBox contextInfo = new VBox(3, date, profile, origin);
        Label status = new Label(overdue ? "Vencida" : item.status().displayName(item.type()));
        status.getStyleClass().addAll("status-pill", "status-" + (overdue ? "overdue" : item.status().name().toLowerCase()));
        Label amount = new Label((item.type() == TransactionType.INCOME ? "+ " : "− ") + MoneyUtils.formatCents(item.amountCents()));
        amount.getStyleClass().addAll("transaction-amount", item.type() == TransactionType.INCOME ? "amount-positive" : "amount-negative");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        MenuButton actions = new MenuButton("Ações");
        actions.getStyleClass().add("ghost-button");
        actions.getItems().add(menu("Ver detalhes", () -> view(item)));
        actions.getItems().add(menu("Editar", () -> showTransactionForm(item)));
        actions.getItems().add(menu("Anexos", () -> attachments(item)));
        if (item.origin() != TransactionOrigin.MANUAL) actions.getItems().add(menu("Gerenciar origem", () -> manageOrigin(item)));
        if (item.status() != TransactionStatus.CANCELLED) {
            actions.getItems().add(menu(item.status() == TransactionStatus.SETTLED ? "Reabrir"
                    : item.type() == TransactionType.INCOME ? "Marcar como recebida" : "Marcar como paga",
                    () -> changeSettlement(item)));
            actions.getItems().add(menu("Cancelar", () -> cancel(item)));
        }
        HBox row = new HBox(12, marker, identity, contextInfo, spacer, status, amount, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("transaction-row");
        if (item.status() == TransactionStatus.CANCELLED) row.getStyleClass().add("transaction-cancelled");
        if (overdue) row.getStyleClass().add("transaction-overdue");
        return row;
    }

    private String originText(Transaction item) {
        if (item.origin() == TransactionOrigin.MANUAL) return "Manual";
        if (item.origin() == TransactionOrigin.RECURRING) return "Recorrência mensal";
        return item.originDetails() == null ? "Parcelada" : item.originDetails();
    }

    private void showAccountForm() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Nova conta");
        dialog.setHeaderText("Registre o compromisso da forma como ele acontece");
        ButtonType saveType = new ButtonType("Registrar conta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField description = new TextField(); description.setPromptText("Ex.: Internet");
        TextField amount = new TextField(); amount.setPromptText("0,00");
        ComboBox<Category> category = expenseCategories();
        ComboBox<Profile> profile = activeProfiles();
        DatePicker due = new DatePicker(LocalDate.now());
        TextArea notes = new TextArea(); notes.setPrefRowCount(2); notes.setWrapText(true);

        ToggleGroup kindGroup = new ToggleGroup();
        ToggleButton unique = kindButton("Única", AccountKind.UNIQUE, kindGroup);
        ToggleButton recurring = kindButton("Recorrente", AccountKind.RECURRING, kindGroup);
        ToggleButton installment = kindButton("Parcelada", AccountKind.INSTALLMENT, kindGroup);
        unique.setSelected(true);
        HBox kindSelector = new HBox(0, unique, recurring, installment);
        kindSelector.getStyleClass().add("segmented-control");

        CheckBox noEnd = new CheckBox("Sem data final"); noEnd.setSelected(true);
        DatePicker endDate = new DatePicker(); endDate.disableProperty().bind(noEnd.selectedProperty());
        VBox recurringOptions = new VBox(9,
                new Label("A conta será gerada mensalmente. O dia do vencimento será mantido sempre que possível."),
                new Label("Término"), new HBox(10, noEnd, endDate));
        recurringOptions.getStyleClass().add("conditional-panel");
        recurringOptions.setVisible(false); recurringOptions.setManaged(false);

        Spinner<Integer> count = new Spinner<>(1, 120, 2); count.setEditable(true); count.setMaxWidth(130);
        VBox preview = new VBox(4); preview.getStyleClass().add("installment-preview");
        VBox installmentOptions = new VBox(9, new Label("Quantidade de parcelas"), count,
                new Label("Prévia das primeiras parcelas"), preview);
        installmentOptions.getStyleClass().add("conditional-panel");
        installmentOptions.setVisible(false); installmentOptions.setManaged(false);

        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("form-error");
        GridPane fields = formGrid();
        int row = 0;
        add(fields, row++, "Descrição", description, true);
        add(fields, row++, "Valor", amount, true);
        add(fields, row++, "Categoria", category, true);
        add(fields, row++, "Perfil", profile, true);
        add(fields, row++, "Vencimento", due, true);
        add(fields, row++, "Observações", notes, false);
        VBox content = new VBox(14, fields, new Separator(), new Label("Tipo da conta"), kindSelector,
                recurringOptions, installmentOptions, error);
        content.getStyleClass().add("dialog-form");

        Runnable updateKind = () -> {
            AccountKind selected = (AccountKind) kindGroup.getSelectedToggle().getUserData();
            toggle(recurringOptions, selected == AccountKind.RECURRING);
            toggle(installmentOptions, selected == AccountKind.INSTALLMENT);
            updateInstallmentPreview(preview, description.getText(), amount.getText(), count.getValue(), due.getValue());
        };
        kindGroup.selectedToggleProperty().addListener((o, oldValue, newValue) -> updateKind.run());
        amount.textProperty().addListener((o, oldValue, newValue) -> updateKind.run());
        due.valueProperty().addListener((o, oldValue, newValue) -> updateKind.run());
        count.valueProperty().addListener((o, oldValue, newValue) -> updateKind.run());
        dialog.getDialogPane().setContent(new ScrollPane(content));
        ThemeManager.apply(dialog, 680, 690);

        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try {
                if (profile.getValue() == null) throw new ValidationException("Selecione o perfil da conta.");
                if (category.getValue() == null) throw new ValidationException("Selecione a categoria da conta.");
                AccountKind selected = (AccountKind) kindGroup.getSelectedToggle().getUserData();
                switch (selected) {
                    case UNIQUE -> context.transactionService().create(new TransactionInput(TransactionType.EXPENSE,
                            description.getText(), MoneyUtils.parseBrazilian(amount.getText()), profile.getValue().id(),
                            category.getValue().id(), due.getValue(), due.getValue(), TransactionStatus.PENDING,
                            null, notes.getText()));
                    case RECURRING -> context.recurringExpenseService().create(new RecurringExpenseInput(
                            description.getText(), MoneyUtils.parseBrazilian(amount.getText()), profile.getValue().id(),
                            category.getValue().id(), due.getValue(), noEnd.isSelected() ? null : endDate.getValue(),
                            notes.getText(), true));
                    case INSTALLMENT -> context.installmentPlanService().create(new InstallmentPlanInput(
                            description.getText(), MoneyUtils.parseBrazilian(amount.getText()), count.getValue(),
                            profile.getValue().id(), category.getValue().id(), due.getValue(), notes.getText()));
                }
                dialog.close();
                showFeedback(selected == AccountKind.UNIQUE ? "Conta registrada."
                        : selected == AccountKind.RECURRING ? "Recorrência mensal criada." : "Parcelamento criado.", false);
                refresh();
            } catch (ValidationException exception) {
                error.setText(exception.getMessage());
            } catch (RuntimeException exception) {
                LOGGER.error("Could not create account", exception);
                error.setText("Não foi possível registrar a conta.");
            }
        });
        dialog.showAndWait();
    }

    private void updateInstallmentPreview(VBox preview, String description, String amount, int count, LocalDate due) {
        preview.getChildren().clear();
        try {
            var items = context.installmentPlanService().preview(description, MoneyUtils.parseBrazilian(amount), count, due);
            items.stream().limit(3).forEach(item -> {
                Label line = new Label(item.sequence() + "ª parcela · " + MoneyUtils.formatCents(item.amountCents())
                        + " · " + DATE.format(item.dueDate()));
                line.getStyleClass().add("section-caption"); preview.getChildren().add(line);
            });
            if (items.size() > 3) preview.getChildren().add(new Label("+ " + (items.size() - 3) + " parcelas seguintes"));
        } catch (RuntimeException ignored) {
            Label hint = new Label("Informe valor, quantidade e vencimento para visualizar a prévia.");
            hint.getStyleClass().add("section-caption"); preview.getChildren().add(hint);
        }
    }

    private void showTransactionForm(Transaction existing) {
        Dialog<Void> dialog = new Dialog<>(); dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(existing == null ? fixedType == TransactionType.INCOME ? "Nova receita" : "Nova movimentação" : "Editar movimentação");
        dialog.setHeaderText(existing == null ? "Informe somente os dados necessários" : "Atualize os dados desta movimentação");
        ButtonType saveType = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        ComboBox<TransactionType> type = new ComboBox<>(); type.getItems().setAll(TransactionType.values());
        type.setValue(existing == null ? (fixedType == null ? TransactionType.EXPENSE : fixedType) : existing.type());
        type.setDisable(fixedType != null); ComboBoxSupport.configure(type, TransactionType::displayName);
        TextField description = new TextField(existing == null ? "" : existing.description());
        TextField amount = new TextField(existing == null ? "" : MoneyUtils.fromCents(existing.amountCents()).toPlainString().replace('.', ','));
        if (existing != null && existing.origin() == TransactionOrigin.INSTALLMENT) amount.setDisable(true);
        ComboBox<Profile> profile = profilesFor(existing); selectProfile(profile, existing);
        ComboBox<Category> category = new ComboBox<>(); ComboBoxSupport.categories(category);
        DatePicker reference = new DatePicker(existing == null ? LocalDate.now() : existing.referenceDate());
        DatePicker due = new DatePicker(existing == null ? null : existing.dueDate());
        ComboBox<TransactionStatus> status = new ComboBox<>(); status.getItems().setAll(TransactionStatus.values());
        status.setValue(existing == null ? TransactionStatus.PENDING : existing.status());
        DatePicker settlement = new DatePicker(existing == null ? null : existing.settlementDate());
        configureStatusLabels(status, type::getValue);
        status.valueProperty().addListener((observable, oldStatus, newStatus) -> {
            if (newStatus == TransactionStatus.SETTLED && settlement.getValue() == null) settlement.setValue(LocalDate.now());
            if (newStatus != TransactionStatus.SETTLED) settlement.setValue(null);
        });
        TextArea notes = new TextArea(existing == null ? "" : existing.notes()); notes.setPrefRowCount(3); notes.setWrapText(true);
        Label error = new Label(); error.getStyleClass().add("form-error"); error.setWrapText(true);
        Label originNotice = new Label(existing == null || existing.origin() == TransactionOrigin.MANUAL ? ""
                : existing.origin() == TransactionOrigin.RECURRING
                ? "Esta ocorrência veio de uma recorrência. A edição afeta somente este mês."
                : "Esta movimentação pertence a um parcelamento. Valor e origem são preservados.");
        originNotice.setWrapText(true); originNotice.getStyleClass().add("info-banner");
        toggle(originNotice, !originNotice.getText().isEmpty());
        Runnable loadCategories = () -> {
            category.getItems().setAll(context.categoryService().search(type.getValue().categoryType(), null, existing != null));
            if (existing != null) category.getItems().stream().filter(c -> c.id().equals(existing.categoryId())).findFirst().ifPresent(category::setValue);
            else category.getItems().stream().filter(Category::active).findFirst().ifPresent(category::setValue);
        };
        type.valueProperty().addListener((o, a, b) -> loadCategories.run()); loadCategories.run();
        GridPane grid = formGrid(); int row = 0;
        add(grid,row++,"Tipo",type,true); add(grid,row++,"Descrição",description,true); add(grid,row++,"Valor",amount,true);
        add(grid,row++,"Perfil",profile,true); add(grid,row++,"Categoria",category,true); add(grid,row++,"Data de referência",reference,true);
        add(grid,row++,"Vencimento",due,false); add(grid,row++,"Situação",status,true); add(grid,row++,"Data de conclusão",settlement,false);
        add(grid,row,"Observações",notes,false);
        VBox content = new VBox(12, originNotice, grid, error); content.getStyleClass().add("dialog-form");
        dialog.getDialogPane().setContent(new ScrollPane(content)); ThemeManager.apply(dialog, 660, 680);
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try {
                TransactionInput input = new TransactionInput(type.getValue(), description.getText(),
                        MoneyUtils.parseBrazilian(amount.getText()), profile.getValue() == null ? 0 : profile.getValue().id(),
                        category.getValue() == null ? 0 : category.getValue().id(), reference.getValue(), due.getValue(),
                        status.getValue(), settlement.getValue(), notes.getText());
                if (existing == null) context.transactionService().create(input); else context.transactionService().update(existing.id(), input);
                dialog.close(); showFeedback(existing == null ? "Movimentação criada." : "Movimentação atualizada.", false); refresh();
            } catch (ValidationException exception) { error.setText(exception.getMessage()); }
            catch (RuntimeException exception) { LOGGER.error("Could not save transaction", exception); error.setText("Não foi possível salvar a movimentação."); }
        });
        dialog.showAndWait();
    }

    private void changeSettlement(Transaction item) {
        try {
            if (item.status() == TransactionStatus.SETTLED) context.transactionService().reopen(item.id());
            else context.transactionService().settle(item.id(), LocalDate.now());
            showFeedback(item.status() == TransactionStatus.SETTLED ? "Movimentação reaberta."
                    : item.type() == TransactionType.INCOME ? "Receita marcada como recebida." : "Conta marcada como paga.", false);
            refresh();
        } catch (RuntimeException exception) { LOGGER.error("Could not change transaction status", exception); showError("Não foi possível alterar a situação."); }
    }

    private void cancel(Transaction item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "O registro continuará disponível no histórico.", ButtonType.CANCEL, ButtonType.OK);
        confirm.setHeaderText("Cancelar “" + item.description() + "”?"); ThemeManager.apply(confirm);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try { context.transactionService().cancel(item.id()); showFeedback("Movimentação cancelada.", false); refresh(); }
            catch (RuntimeException exception) { LOGGER.error("Could not cancel transaction", exception); showError("Não foi possível cancelar a movimentação."); }
        }
    }

    private void view(Transaction item) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Detalhes da movimentação");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox content = new VBox(10,
                detail("Descrição", item.description()), detail("Tipo", item.type().displayName()),
                detail("Valor", MoneyUtils.formatCents(item.amountCents())), detail("Situação", item.status().displayName(item.type())),
                detail("Data", DATE.format(item.referenceDate())), detail("Perfil", item.profileName()),
                detail("Categoria", item.categoryName()), detail("Origem", originText(item)),
                detail("Observações", item.notes() == null ? "—" : item.notes()));
        content.getStyleClass().add("details-panel"); dialog.getDialogPane().setContent(content);
        ThemeManager.apply(dialog, 540, 0); dialog.showAndWait();
    }

    private HBox detail(String label, String value) {
        Label key = new Label(label); key.getStyleClass().add("detail-label"); key.setMinWidth(130);
        Label text = new Label(value); text.setWrapText(true); HBox.setHgrow(text, Priority.ALWAYS);
        return new HBox(12, key, text);
    }

    private void manageOrigin(Transaction item) {
        if (item.origin() == TransactionOrigin.RECURRING) showCommitmentManager(true);
        else showCommitmentManager(false);
    }

    private void showCommitmentManager(boolean recurring) {
        try {
            String resource = recurring ? "/fxml/recurring-expenses-view.fxml" : "/fxml/installment-plans-view.fxml";
            FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
            Parent view = loader.load();
            if (recurring) ((RecurringExpensesController) loader.getController()).configure(context);
            else ((InstallmentPlansController) loader.getController()).configure(context);
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle(recurring ? "Gerenciar recorrências" : "Gerenciar parcelamentos");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setContent(view);
            ThemeManager.apply(dialog, 900, 680); dialog.showAndWait(); refresh();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not open commitment manager", exception);
            showError("Não foi possível abrir o gerenciamento.");
        }
    }

    private void attachments(Transaction item) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Anexos · " + item.description());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox files = new VBox(8); Button add = new Button("Adicionar arquivos"); add.getStyleClass().add("secondary-button");
        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            var attachments = context.attachmentService().search(new AttachmentFilter(null, item.profileId(), null,
                    item.type(), null, null, true, null)).stream().filter(a -> item.id().equals(a.transactionId())).toList();
            files.getChildren().clear();
            if (attachments.isEmpty()) files.getChildren().add(new Label("Nenhum anexo nesta movimentação."));
            attachments.forEach(file -> {
                Label label = new Label(file.originalFilename() + " · " + file.integrity().displayName());
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                Button open = textButton("Abrir", () -> context.attachmentService().openExternal(file.id())
                        .exceptionally(error -> { Platform.runLater(() -> showError("Não foi possível abrir o anexo.")); return null; }));
                Button remove = textButton("Remover", () -> { context.attachmentService().remove(file.id()); load[0].run(); });
                HBox line = new HBox(8, label, spacer, open, remove); line.setAlignment(Pos.CENTER_LEFT); line.getStyleClass().add("compact-row");
                files.getChildren().add(line);
            });
        };
        add.setOnAction(event -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle("Adicionar anexos");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF e imagens", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
            var selected = chooser.showOpenMultipleDialog(dialog.getOwner());
            if (selected != null) try {
                DocumentType suggested = item.type() == TransactionType.INCOME ? DocumentType.PAYSLIP
                        : item.status() == TransactionStatus.PENDING ? DocumentType.INVOICE : DocumentType.RECEIPT;
                for (var file : selected) context.attachmentService().importForTransaction(item.id(), suggested, null, file.toPath());
                load[0].run();
            } catch (RuntimeException exception) { showError(exception.getMessage()); }
        });
        VBox content = new VBox(12, add, files); content.getStyleClass().add("dialog-form"); load[0].run();
        dialog.getDialogPane().setContent(content); ThemeManager.apply(dialog, 650, 500); dialog.showAndWait();
    }

    private ComboBox<Profile> activeProfiles() {
        ComboBox<Profile> box = new ComboBox<>();
        box.getItems().setAll(context.profileService().listProfiles().stream().filter(Profile::active).toList());
        ComboBoxSupport.profiles(box); box.getItems().stream().findFirst().ifPresent(box::setValue); return box;
    }

    private ComboBox<Profile> profilesFor(Transaction existing) {
        if (existing == null) return activeProfiles();
        ComboBox<Profile> box = new ComboBox<>();
        box.getItems().setAll(context.profileService().listProfiles());
        ComboBoxSupport.profiles(box);
        return box;
    }

    private ComboBox<Category> expenseCategories() {
        ComboBox<Category> box = new ComboBox<>(); box.getItems().setAll(context.categoryService().search(CategoryType.EXPENSE, null, false));
        ComboBoxSupport.categories(box); box.getItems().stream().findFirst().ifPresent(box::setValue); return box;
    }

    private ToggleButton kindButton(String text, AccountKind kind, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text); button.setUserData(kind); button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(button, Priority.ALWAYS); return button;
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane(); grid.setHgap(16); grid.setVgap(11); grid.setMaxWidth(620); return grid;
    }

    private void add(GridPane grid, int row, String text, Control control, boolean required) {
        Label label = new Label(text + (required ? " *" : "")); label.getStyleClass().add("field-label"); label.setMinWidth(145);
        control.setMaxWidth(Double.MAX_VALUE); grid.add(label, 0, row); grid.add(control, 1, row); GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private Button textButton(String text, Runnable action) {
        Button button = new Button(text); button.getStyleClass().add("text-button"); button.setOnAction(event -> action.run()); return button;
    }

    private MenuItem menu(String text, Runnable runnable) { MenuItem item = new MenuItem(text); item.setOnAction(event -> runnable.run()); return item; }
    private Long id(Profile profile) { return profile == null ? null : profile.id(); }
    private Long id(Category category) { return category == null ? null : category.id(); }
    private void selectProfile(ComboBox<Profile> box, Transaction transaction) {
        if (transaction != null) box.getItems().stream().filter(p -> p.id().equals(transaction.profileId())).findFirst().ifPresent(box::setValue);
    }
    private void loadCategoryFilter() {
        if (context == null) return;
        TransactionType type = fixedType == null ? typeFilter.getValue() : fixedType;
        categoryFilter.getItems().setAll(context.categoryService().search(type == null ? null : type.categoryType(), null, false));
    }
    private void configureStatusLabels(ComboBox<TransactionStatus> combo, java.util.function.Supplier<TransactionType> type) {
        ComboBoxSupport.configure(combo, status -> type.get() == null ? switch (status) {
            case PENDING -> "Pendente"; case SETTLED -> "Concluída"; case CANCELLED -> "Cancelada";
        } : status.displayName(type.get()));
    }
    private void showFeedback(String message, boolean error) {
        feedback.setText(message); feedback.getStyleClass().removeAll("form-error", "form-success");
        feedback.getStyleClass().add(error ? "form-error" : "form-success");
    }
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? "Não foi possível concluir a operação." : message, ButtonType.OK);
        alert.setHeaderText("Não foi possível concluir"); ThemeManager.apply(alert); alert.showAndWait();
    }
    private void toggle(Region node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }

    private enum AccountKind { UNIQUE, RECURRING, INSTALLMENT }
}
