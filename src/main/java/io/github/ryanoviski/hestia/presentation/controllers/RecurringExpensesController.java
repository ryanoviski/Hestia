package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.dto.RecurringExpenseInput;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.domain.models.RecurringExpense;
import io.github.ryanoviski.hestia.presentation.components.ComboBoxSupport;
import io.github.ryanoviski.hestia.presentation.components.DatePickerSupport;
import io.github.ryanoviski.hestia.presentation.components.DialogSupport;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class RecurringExpensesController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    @FXML private TextField searchField;
    @FXML private ComboBox<Profile> profileFilter;
    @FXML private ComboBox<Category> categoryFilter;
    @FXML private VBox list;
    @FXML private Label emptyState;
    private ApplicationContext context;

    public void configure(ApplicationContext context) {
        this.context = context;
        ComboBoxSupport.profiles(profileFilter);
        ComboBoxSupport.categories(categoryFilter);
        profileFilter.getItems().setAll(context.profileService().listProfiles());
        categoryFilter.getItems().setAll(context.categoryService().search(CategoryType.EXPENSE, null, false));
        refresh();
    }

    @FXML private void refresh() {
        if (context == null) return;
        var items = context.recurringExpenseService().search(searchField.getText(), id(profileFilter.getValue()), id(categoryFilter.getValue()));
        list.getChildren().clear();
        items.forEach(item -> list.getChildren().add(row(item)));
        emptyState.setVisible(items.isEmpty());
        emptyState.setManaged(items.isEmpty());
        if (items.isEmpty()) list.getChildren().add(emptyState);
    }

    @FXML private void create() { form(null); }

    private HBox row(RecurringExpense item) {
        Label name = new Label(item.description());
        name.getStyleClass().add("transaction-description");
        String next = item.nextDueDate() == null ? "Sem próximo vencimento" : "Próximo: " + DATE.format(item.nextDueDate());
        Label details = new Label(item.profileName() + " · " + item.categoryName() + " · " + next);
        details.setWrapText(true);
        VBox info = new VBox(4, name, details);
        Label amount = new Label(MoneyUtils.formatCents(item.amountCents()));
        amount.getStyleClass().add("transaction-amount");
        Label active = new Label(item.active() ? "Ativa" : "Inativa");
        active.getStyleClass().addAll("status-pill", item.active() ? "status-settled" : "status-cancelled");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        MenuButton actions = new MenuButton("Ações");
        actions.getStyleClass().add("secondary-button");
        actions.getItems().add(menu("Editar", () -> form(item)));
        actions.getItems().add(menu(item.active() ? "Desativar" : "Reativar", () -> toggle(item)));
        HBox row = new HBox(10, info, spacer, active, amount, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("transaction-row");
        return row;
    }

    private void toggle(RecurringExpense item) {
        boolean cancelPending = false;
        if (item.active()) {
            ButtonType deactivate = new ButtonType("Somente desativar");
            ButtonType cancel = new ButtonType("Desativar e cancelar pendentes");
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Escolha se as ocorrências futuras ainda pendentes também devem ser canceladas.",
                    new ButtonType("Voltar", ButtonBar.ButtonData.CANCEL_CLOSE), deactivate, cancel);
            alert.setTitle("Desativar recorrência");
            alert.setHeaderText("Desativar recorrência");
            DialogSupport.prepare(alert, list, 540, 0);
            DialogSupport.markDestructive(alert, cancel);
            ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
            if (answer.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) return;
            cancelPending = answer == cancel;
        }
        context.recurringExpenseService().setActive(item.id(), !item.active(), cancelPending);
        refresh();
    }

    private void form(RecurringExpense existing) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Nova recorrência" : "Editar recorrência");
        dialog.setHeaderText(existing == null ? "Nova conta recorrente" : "Editar conta recorrente");
        ButtonType saveType = DialogSupport.primaryAction(existing == null ? "Criar recorrência" : "Salvar alterações");
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, saveType);

        TextField description = new TextField(existing == null ? "" : existing.description());
        description.setPromptText("Ex.: Aluguel");
        TextField amount = new TextField(existing == null ? "" : existing.amount().toPlainString().replace('.', ','));
        amount.setPromptText("R$ 0,00"); amount.getStyleClass().add("money-field");
        ComboBox<Profile> profile = new ComboBox<>();
        profile.getItems().setAll(context.profileService().listProfiles());
        ComboBoxSupport.profiles(profile);
        ComboBox<Category> category = new ComboBox<>();
        category.getItems().setAll(context.categoryService().search(CategoryType.EXPENSE, null, existing != null));
        ComboBoxSupport.categories(category);
        select(profile, category, existing);
        DatePicker first = DatePickerSupport.configure(new DatePicker(existing == null ? LocalDate.now() : existing.firstDueDate()));
        DatePicker end = DatePickerSupport.configure(new DatePicker(existing == null ? null : existing.endDate()));
        TextArea notes = new TextArea(existing == null ? "" : existing.notes());
        notes.setPrefRowCount(2);
        notes.setWrapText(true);
        CheckBox active = new CheckBox("Recorrência ativa");
        active.setSelected(existing == null || existing.active());
        Label error = new Label();
        error.setWrapText(true);
        error.getStyleClass().add("form-error");

        VBox information = DialogSupport.section("Informações da conta", null,
                DialogSupport.field("Descrição", description, true),
                DialogSupport.columns(DialogSupport.field("Valor previsto", amount, true),
                        DialogSupport.field("Primeiro vencimento", first, true)),
                DialogSupport.columns(DialogSupport.field("Perfil", profile, true),
                        DialogSupport.field("Categoria", category, true)));
        VBox repetition = DialogSupport.section("Repetição", existing == null
                        ? "O Hestia gerará ocorrências mensais usando as regras atuais do serviço."
                        : "A alteração alcança somente ocorrências futuras, pendentes e ainda não personalizadas.",
                DialogSupport.field("Data final (opcional)", end, false), active);
        VBox notesSection = DialogSupport.section("Observações", null,
                DialogSupport.field("Informações adicionais", notes, false));
        VBox content = DialogSupport.content("Organize um compromisso mensal sem criar lançamentos manualmente.",
                information, repetition, notesSection, error);
        dialog.getDialogPane().setContent(DialogSupport.scrollRegion(content, 455));
        DialogSupport.prepare(dialog, list, 690, 570);

        ((Button) dialog.getDialogPane().lookupButton(saveType)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try {
                var input = new RecurringExpenseInput(description.getText(), MoneyUtils.parseBrazilian(amount.getText()),
                        profile.getValue() == null ? 0 : profile.getValue().id(), category.getValue() == null ? 0 : category.getValue().id(),
                        first.getValue(), end.getValue(), notes.getText(), active.isSelected());
                if (existing == null) context.recurringExpenseService().create(input);
                else context.recurringExpenseService().update(existing.id(), input);
                dialog.close();
                refresh();
            } catch (ValidationException exception) {
                error.setText(exception.getMessage());
            }
        });
        dialog.showAndWait();
    }

    private void select(ComboBox<Profile> profiles, ComboBox<Category> categories, RecurringExpense existing) {
        if (existing == null) {
            profiles.getItems().stream().filter(Profile::active).findFirst().ifPresent(profiles::setValue);
            categories.getItems().stream().filter(Category::active).findFirst().ifPresent(categories::setValue);
        } else {
            profiles.getItems().stream().filter(item -> item.id().equals(existing.profileId())).findFirst().ifPresent(profiles::setValue);
            categories.getItems().stream().filter(item -> item.id().equals(existing.categoryId())).findFirst().ifPresent(categories::setValue);
        }
    }

    private MenuItem menu(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private Long id(Profile profile) { return profile == null ? null : profile.id(); }
    private Long id(Category category) { return category == null ? null : category.id(); }
}
