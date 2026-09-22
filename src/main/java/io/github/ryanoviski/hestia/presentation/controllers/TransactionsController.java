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
import io.github.ryanoviski.hestia.presentation.components.DatePickerSupport;
import io.github.ryanoviski.hestia.presentation.components.DialogSupport;
import io.github.ryanoviski.hestia.presentation.components.MonthYearPicker;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
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
        dialog.setTitle("Nova conta");
        dialog.setHeaderText("Nova conta a pagar");
        ButtonType saveType = DialogSupport.primaryAction("Registrar conta");
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, saveType);

        TextField description = new TextField(); description.setPromptText("Ex.: Internet");
        TextField amount = new TextField(); amount.setPromptText("R$ 0,00"); amount.getStyleClass().add("money-field");
        ComboBox<Category> category = expenseCategories();
        ComboBox<Profile> profile = activeProfiles();
        DatePicker due = DatePickerSupport.configure(new DatePicker(LocalDate.now()));
        TextArea notes = new TextArea(); notes.setPrefRowCount(2); notes.setWrapText(true);

        ToggleGroup kindGroup = new ToggleGroup();
        ToggleButton unique = kindButton("Única", AccountKind.UNIQUE, kindGroup);
        ToggleButton recurring = kindButton("Recorrente", AccountKind.RECURRING, kindGroup);
        ToggleButton installment = kindButton("Parcelada", AccountKind.INSTALLMENT, kindGroup);
        unique.setSelected(true);
        HBox kindSelector = new HBox(0, unique, recurring, installment);
        kindSelector.getStyleClass().add("segmented-control");

        CheckBox noEnd = new CheckBox("Sem data final"); noEnd.setSelected(true);
        DatePicker endDate = DatePickerSupport.configure(new DatePicker());
        endDate.disableProperty().bind(noEnd.selectedProperty());
        VBox recurringOptions = DialogSupport.section("Repetição mensal",
                "O vencimento será repetido mensalmente, respeitando os dias disponíveis em cada mês.",
                DialogSupport.columns(DialogSupport.field("Término", endDate, false), noEnd));
        recurringOptions.getStyleClass().add("conditional-panel");
        recurringOptions.setVisible(false); recurringOptions.setManaged(false);

        Spinner<Integer> count = new Spinner<>(1, 120, 2); count.setEditable(true); count.setMaxWidth(130);
        VBox preview = new VBox(4); preview.getStyleClass().add("installment-preview");
        VBox installmentOptions = DialogSupport.section("Resumo do parcelamento",
                "O Hestia distribui o total em centavos exatos usando as regras atuais do serviço.",
                DialogSupport.field("Quantidade de parcelas", count, true), preview);
        installmentOptions.getStyleClass().add("conditional-panel");
        installmentOptions.setVisible(false); installmentOptions.setManaged(false);

        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("form-error");
        VBox typeSection = DialogSupport.section("Tipo da conta",
                "Escolha somente as opções necessárias para este compromisso.", kindSelector);
        VBox fields = DialogSupport.section("Informações da conta", null,
                DialogSupport.field("Descrição", description, true),
                DialogSupport.columns(DialogSupport.field("Valor", amount, true),
                        DialogSupport.field("Primeiro vencimento", due, true)),
                DialogSupport.columns(DialogSupport.field("Perfil", profile, true),
                        DialogSupport.field("Categoria", category, true)),
                DialogSupport.field("Observações", notes, false));
        VBox content = DialogSupport.content("Registre uma conta única, recorrente ou parcelada no mesmo fluxo.",
                typeSection, fields, recurringOptions, installmentOptions, error);

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
        dialog.getDialogPane().setContent(DialogSupport.scrollRegion(content, 455));
        DialogSupport.prepare(dialog, transactionList, 700, 570);

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
        Dialog<Void> dialog = new Dialog<>();
        String title = existing == null ? fixedType == TransactionType.INCOME ? "Nova receita" : "Nova movimentação"
                : existing.type() == TransactionType.INCOME ? "Editar receita" : "Editar movimentação";
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        ButtonType saveType = DialogSupport.primaryAction(existing == null ? "Registrar" : "Salvar alterações");
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, saveType);
        ComboBox<TransactionType> type = new ComboBox<>(); type.getItems().setAll(TransactionType.values());
        type.setValue(existing == null ? (fixedType == null ? TransactionType.EXPENSE : fixedType) : existing.type());
        type.setDisable(fixedType != null); ComboBoxSupport.configure(type, TransactionType::displayName);
        TextField description = new TextField(existing == null ? "" : existing.description());
        description.setPromptText("Ex.: Supermercado");
        TextField amount = new TextField(existing == null ? "" : MoneyUtils.fromCents(existing.amountCents()).toPlainString().replace('.', ','));
        amount.setPromptText("R$ 0,00"); amount.getStyleClass().add("money-field");
        if (existing != null && existing.origin() == TransactionOrigin.INSTALLMENT) amount.setDisable(true);
        ComboBox<Profile> profile = profilesFor(existing); selectProfile(profile, existing);
        ComboBox<Category> category = new ComboBox<>(); ComboBoxSupport.categories(category);
        DatePicker reference = DatePickerSupport.configure(new DatePicker(existing == null ? LocalDate.now() : existing.referenceDate()));
        DatePicker due = DatePickerSupport.configure(new DatePicker(existing == null ? null : existing.dueDate()));
        ComboBox<TransactionStatus> status = new ComboBox<>(); status.getItems().setAll(TransactionStatus.values());
        status.setValue(existing == null ? TransactionStatus.PENDING : existing.status());
        DatePicker settlement = DatePickerSupport.configure(new DatePicker(existing == null ? null : existing.settlementDate()));
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
        VBox dueField = DialogSupport.field(type.getValue() == TransactionType.INCOME ? "Data prevista" : "Vencimento", due, false);
        VBox settlementField = DialogSupport.field("Data de conclusão", settlement, false);
        Runnable updateVisibility = () -> {
            boolean settled = status.getValue() == TransactionStatus.SETTLED;
            settlementField.setVisible(settled); settlementField.setManaged(settled);
            boolean expense = type.getValue() == TransactionType.EXPENSE;
            dueField.setVisible(expense); dueField.setManaged(expense);
        };
        type.valueProperty().addListener((o, oldValue, newValue) -> updateVisibility.run());
        status.valueProperty().addListener((o, oldValue, newValue) -> updateVisibility.run());
        updateVisibility.run();

        VBox mainSection = DialogSupport.section("Movimentação", null,
                DialogSupport.columns(DialogSupport.field("Tipo", type, true),
                        DialogSupport.field("Valor", amount, true)),
                DialogSupport.field("Descrição", description, true));
        VBox classification = DialogSupport.section("Classificação", null,
                DialogSupport.columns(DialogSupport.field("Perfil", profile, true),
                        DialogSupport.field("Categoria", category, true)));
        VBox dates = DialogSupport.section("Datas e situação", null,
                DialogSupport.columns(DialogSupport.field("Data de referência", reference, true), dueField),
                DialogSupport.columns(DialogSupport.field("Situação", status, true), settlementField));
        VBox notesSection = DialogSupport.section("Observações", null,
                DialogSupport.field("Informações adicionais", notes, false));
        VBox content = DialogSupport.content(existing == null
                        ? "Registre somente as informações necessárias para acompanhar este lançamento."
                        : "Atualize os dados preservando a origem e o histórico da movimentação.",
                originNotice, mainSection, classification, dates, notesSection, error);
        dialog.getDialogPane().setContent(DialogSupport.scrollRegion(content, 455));
        DialogSupport.prepare(dialog, transactionList, 700, 570);
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
        if (item.status() == TransactionStatus.SETTLED) {
            boolean confirmed = DialogSupport.confirm(transactionList, "Reabrir movimentação",
                    "Reabrir “" + item.description() + "”?",
                    "A movimentação voltará a ficar pendente e a data de conclusão será removida.",
                    "Reabrir", false);
            if (!confirmed) return;
            try {
                context.transactionService().reopen(item.id());
                showFeedback("Movimentação reaberta.", false);
                refresh();
            } catch (RuntimeException exception) {
                LOGGER.error("Could not reopen transaction", exception);
                showError("Não foi possível reabrir a movimentação.");
            }
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        boolean income = item.type() == TransactionType.INCOME;
        dialog.setTitle(income ? "Marcar como recebida" : "Marcar como paga");
        dialog.setHeaderText(income ? "Confirmar recebimento" : "Confirmar pagamento");
        ButtonType confirmType = DialogSupport.primaryAction(income ? "Confirmar recebimento" : "Confirmar pagamento");
        ButtonType cancelType = new ButtonType("Voltar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, confirmType);
        DatePicker date = DatePickerSupport.configure(new DatePicker(LocalDate.now()));
        Label name = new Label(item.description()); name.getStyleClass().add("transaction-description");
        Label value = new Label(MoneyUtils.formatCents(item.amountCents())); value.getStyleClass().add("money-highlight");
        VBox summary = new VBox(4, name, value); summary.getStyleClass().add("settlement-summary");
        VBox content = DialogSupport.content(income
                        ? "Informe a data em que a receita foi efetivamente recebida."
                        : "Informe a data em que a conta foi efetivamente paga.",
                summary, DialogSupport.field(income ? "Data do recebimento" : "Data do pagamento", date, true));
        dialog.getDialogPane().setContent(content);
        DialogSupport.prepare(dialog, transactionList, 500, 0);
        Button confirm = (Button) dialog.getDialogPane().lookupButton(confirmType);
        confirm.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            if (date.getValue() == null) return;
            try {
                context.transactionService().settle(item.id(), date.getValue());
                dialog.close();
                showFeedback(income ? "Receita marcada como recebida." : "Conta marcada como paga.", false);
                refresh();
            } catch (RuntimeException exception) {
                LOGGER.error("Could not settle transaction", exception);
                showError("Não foi possível alterar a situação.");
            }
        });
        dialog.showAndWait();
    }

    private void cancel(Transaction item) {
        boolean confirmed = DialogSupport.confirm(transactionList, "Cancelar movimentação",
                "Cancelar “" + item.description() + "”?",
                "A movimentação deixará de participar dos resultados financeiros, mas continuará disponível no histórico.",
                "Cancelar movimentação", true);
        if (confirmed) {
            try { context.transactionService().cancel(item.id()); showFeedback("Movimentação cancelada.", false); refresh(); }
            catch (RuntimeException exception) { LOGGER.error("Could not cancel transaction", exception); showError("Não foi possível cancelar a movimentação."); }
        }
    }

    private void view(Transaction item) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Detalhes da movimentação");
        dialog.setHeaderText("Detalhes da movimentação");
        ButtonType editType = DialogSupport.primaryAction("Editar");
        ButtonType closeType = new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(closeType, editType);

        boolean overdue = item.isOverdue(Clock.systemDefaultZone());
        Label title = new Label(item.description()); title.getStyleClass().add("detail-title");
        Label amount = new Label(MoneyUtils.formatCents(item.amountCents())); amount.getStyleClass().add("money-highlight");
        Label badge = DialogSupport.statusBadge(overdue ? "Vencida" : item.status().displayName(item.type()),
                "status-" + (overdue ? "overdue" : item.status().name().toLowerCase()));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heroHeader = new HBox(10, title, spacer, badge); heroHeader.setAlignment(Pos.CENTER_LEFT);
        VBox hero = new VBox(6, heroHeader, amount); hero.getStyleClass().add("detail-hero");

        VBox information = DialogSupport.section("Informações", null,
                detail("Tipo", item.type().displayName()), detail("Perfil", item.profileName()),
                detail("Categoria", item.categoryName()), detail("Data de referência", DATE.format(item.referenceDate())),
                detail("Vencimento", item.dueDate() == null ? "—" : DATE.format(item.dueDate())),
                detail(item.type() == TransactionType.INCOME ? "Recebimento" : "Pagamento",
                        item.settlementDate() == null ? "—" : DATE.format(item.settlementDate())));
        VBox origin = DialogSupport.section("Origem", null, detail("Registro", originText(item)));
        Label notes = new Label(item.notes() == null || item.notes().isBlank() ? "Nenhuma observação." : item.notes());
        notes.setWrapText(true); notes.getStyleClass().add("detail-value");
        VBox notesSection = DialogSupport.section("Observações", null, notes);
        Button attachmentsButton = new Button("Gerenciar anexos"); attachmentsButton.getStyleClass().add("secondary-button");
        attachmentsButton.setOnAction(event -> attachments(item));
        VBox attachmentsSection = DialogSupport.section("Anexos", "Arquivos permanecem vinculados a esta movimentação.", attachmentsButton);
        VBox content = DialogSupport.content("Consulte os dados organizados e edite quando necessário.",
                hero, information, origin, notesSection, attachmentsSection);
        dialog.getDialogPane().setContent(DialogSupport.scrollRegion(content, 470));
        DialogSupport.prepare(dialog, transactionList, 620, 580);
        if (dialog.showAndWait().orElse(closeType) == editType) showTransactionForm(item);
    }

    private HBox detail(String label, String value) {
        Label key = new Label(label); key.getStyleClass().add("detail-label"); key.setMinWidth(145);
        Label text = new Label(value); text.setWrapText(true); text.getStyleClass().add("detail-value");
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(12, key, text); row.getStyleClass().add("detail-row");
        return row;
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
            dialog.setHeaderText(recurring ? "Recorrências desta conta" : "Parcelamentos e parcelas");
            dialog.getDialogPane().getButtonTypes().add(new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
            dialog.getDialogPane().setContent(view);
            DialogSupport.prepare(dialog, transactionList, 860, 560); dialog.showAndWait(); refresh();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not open commitment manager", exception);
            showError("Não foi possível abrir o gerenciamento.");
        }
    }

    private void attachments(Transaction item) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Anexos · " + item.description());
        dialog.setHeaderText("Anexos da movimentação");
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
        VBox files = new VBox(8); files.getStyleClass().add("attachment-list");
        Button add = new Button("Adicionar anexo"); add.getStyleClass().add("primary-button");
        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            var attachments = context.attachmentService().search(new AttachmentFilter(null, item.profileId(), null,
                    item.type(), null, null, true, null)).stream().filter(a -> item.id().equals(a.transactionId())).toList();
            files.getChildren().clear();
            if (attachments.isEmpty()) {
                Label empty = new Label("Nenhum anexo nesta movimentação.");
                empty.getStyleClass().add("empty-state");
                files.getChildren().add(empty);
            }
            attachments.forEach(file -> {
                Label label = new Label(file.originalFilename()); label.getStyleClass().add("attachment-name");
                Label metadata = new Label(file.fileExtension().toUpperCase() + " · " + formatSize(file.sizeBytes())
                        + " · " + file.integrity().displayName());
                metadata.getStyleClass().add("attachment-meta");
                VBox identity = new VBox(2, label, metadata);
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                Button open = textButton("Abrir", () -> context.attachmentService().openExternal(file.id())
                        .exceptionally(error -> { Platform.runLater(() -> showError("Não foi possível abrir o anexo.")); return null; }));
                Button remove = textButton("Remover", () -> {
                    if (DialogSupport.confirm(files, "Remover anexo", "Remover “" + file.originalFilename() + "”?",
                            "O arquivo será removido, mas a movimentação será preservada.", "Remover", true)) {
                        context.attachmentService().remove(file.id()); load[0].run();
                    }
                });
                remove.getStyleClass().add("destructive-button");
                HBox line = new HBox(8, identity, spacer, open, remove); line.setAlignment(Pos.CENTER_LEFT);
                line.getStyleClass().add("attachment-row");
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
        VBox content = DialogSupport.content("Adicione comprovantes, notas e imagens relacionados a este registro.",
                add, DialogSupport.scrollRegion(files, 300));
        load[0].run();
        dialog.getDialogPane().setContent(content);
        DialogSupport.prepare(dialog, transactionList, 680, 500); dialog.showAndWait();
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
        alert.setHeaderText("Não foi possível concluir"); DialogSupport.prepare(alert, transactionList, 500, 0); alert.showAndWait();
    }
    private String formatSize(long size) {
        return size < 1024 ? size + " B" : size < 1024 * 1024
                ? String.format("%.1f KB", size / 1024d) : String.format("%.1f MB", size / 1024d / 1024d);
    }
    private void toggle(Region node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }

    private enum AccountKind { UNIQUE, RECURRING, INSTALLMENT }
}
