package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.dto.InstallmentPlanInput;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.InstallmentPlan;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.presentation.components.ComboBoxSupport;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class InstallmentPlansController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    @FXML private VBox list;
    @FXML private Label emptyState;
    private ApplicationContext context;

    public void configure(ApplicationContext context) { this.context = context; refresh(); }

    private void refresh() {
        var items = context.installmentPlanService().list();
        list.getChildren().clear();
        items.forEach(item -> list.getChildren().add(row(item)));
        emptyState.setVisible(items.isEmpty());
        emptyState.setManaged(items.isEmpty());
        if (items.isEmpty()) list.getChildren().add(emptyState);
    }

    @FXML private void create() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Nova compra parcelada");
        dialog.setHeaderText("Cadastre um compromisso parcelado independente");
        ButtonType saveType = new ButtonType("Gerar parcelas", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        TextField description = new TextField();
        TextField amount = new TextField();
        Spinner<Integer> count = new Spinner<>(1, 120, 2);
        count.setEditable(true);
        ComboBox<Profile> profile = new ComboBox<>();
        profile.getItems().setAll(context.profileService().listProfiles().stream().filter(Profile::active).toList());
        ComboBoxSupport.profiles(profile);
        profile.getItems().stream().findFirst().ifPresent(profile::setValue);
        ComboBox<Category> category = new ComboBox<>();
        category.getItems().setAll(context.categoryService().search(CategoryType.EXPENSE, null, false));
        ComboBoxSupport.categories(category);
        category.getItems().stream().findFirst().ifPresent(category::setValue);
        DatePicker first = new DatePicker(LocalDate.now());
        TextArea notes = new TextArea();
        notes.setPrefRowCount(3);
        notes.setWrapText(true);
        Label hint = new Label("As parcelas serão compromissos próprios do Hestia, sem vínculo com banco ou cartão.");
        hint.setWrapText(true);
        hint.getStyleClass().add("origin-notice");
        Label error = new Label();
        error.setWrapText(true);
        error.getStyleClass().add("form-error");
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(11);
        grid.setPadding(new Insets(8));
        int row = 0;
        grid.add(hint, 0, row++, 2, 1);
        add(grid, row++, "Descrição *", description);
        add(grid, row++, "Valor total *", amount);
        add(grid, row++, "Quantidade de parcelas *", count);
        add(grid, row++, "Perfil *", profile);
        add(grid, row++, "Categoria *", category);
        add(grid, row++, "Primeiro vencimento *", first);
        add(grid, row++, "Observações", notes);
        grid.add(error, 1, row);
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        dialog.getDialogPane().setContent(scroll);
        ThemeManager.apply(dialog, 660, 620);
        ((Button) dialog.getDialogPane().lookupButton(saveType)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try {
                context.installmentPlanService().create(new InstallmentPlanInput(description.getText(),
                        MoneyUtils.parseBrazilian(amount.getText()), count.getValue(),
                        profile.getValue() == null ? 0 : profile.getValue().id(),
                        category.getValue() == null ? 0 : category.getValue().id(), first.getValue(), notes.getText()));
                dialog.close();
                refresh();
            } catch (ValidationException exception) {
                error.setText(exception.getMessage());
            }
        });
        dialog.showAndWait();
    }

    private HBox row(InstallmentPlan plan) {
        Label name = new Label(plan.description());
        name.getStyleClass().add("transaction-description");
        Label summary = new Label(plan.profileName() + " · " + plan.categoryName() + " · " + plan.installmentCount() + " parcelas");
        summary.setWrapText(true);
        Label progress = new Label("Pagas: " + plan.paidCount() + " · Pendentes: " + plan.pendingCount()
                + " · Próximo: " + (plan.nextDueDate() == null ? "—" : DATE.format(plan.nextDueDate())));
        progress.setWrapText(true);
        VBox info = new VBox(4, name, summary, progress);
        Label status = new Label(plan.calculatedStatus().displayName());
        status.getStyleClass().addAll("status-pill", "status-pending");
        Label total = new Label(MoneyUtils.formatCents(plan.totalAmountCents()));
        total.getStyleClass().add("transaction-amount");
        Label totals = new Label("Pago " + MoneyUtils.formatCents(plan.paidAmountCents()) + " · restante " + MoneyUtils.formatCents(plan.remainingAmountCents()));
        totals.setWrapText(true);
        VBox money = new VBox(3, total, totals);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        MenuButton actions = new MenuButton("Ações");
        actions.getStyleClass().add("secondary-button");
        actions.getItems().add(menu("Ver parcelas", () -> details(plan)));
        actions.getItems().add(menu("Cancelar restantes", () -> cancel(plan)));
        HBox row = new HBox(10, info, spacer, status, money, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("transaction-row");
        return row;
    }

    private void details(InstallmentPlan plan) {
        StringBuilder text = new StringBuilder();
        for (var installment : context.installmentPlanService().installments(plan.id())) {
            text.append(installment.number()).append('/').append(plan.installmentCount()).append(" · ")
                    .append(DATE.format(installment.dueDate())).append(" · ")
                    .append(MoneyUtils.formatCents(installment.plannedAmountCents())).append(" · ")
                    .append(installment.status().displayName(TransactionType.EXPENSE)).append('\n');
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, text.toString(), ButtonType.OK);
        alert.setHeaderText(plan.description());
        alert.setTitle("Parcelas");
        ThemeManager.apply(alert, 620, 500);
        alert.showAndWait();
    }

    private void cancel(InstallmentPlan plan) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Somente parcelas atuais ou futuras ainda pendentes serão canceladas. Parcelas pagas serão preservadas.",
                ButtonType.CANCEL, ButtonType.OK);
        alert.setHeaderText("Cancelar parcelas restantes");
        ThemeManager.apply(alert);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            context.installmentPlanService().cancelRemaining(plan.id());
            refresh();
        }
    }

    private void add(GridPane grid, int row, String text, Control control) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        label.setMinWidth(180);
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
}
