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
import io.github.ryanoviski.hestia.presentation.components.DatePickerSupport;
import io.github.ryanoviski.hestia.presentation.components.DialogSupport;
import io.github.ryanoviski.hestia.presentation.components.MoneyTextField;
import io.github.ryanoviski.hestia.presentation.components.FormValidationSupport;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstallmentPlansController {
    private static final Logger LOGGER=LoggerFactory.getLogger(InstallmentPlansController.class);
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
        dialog.setHeaderText("Nova conta parcelada");
        ButtonType saveType = DialogSupport.primaryAction("Gerar parcelas");
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, saveType);
        TextField description = new TextField();
        description.setPromptText("Ex.: Notebook");
        MoneyTextField amount = new MoneyTextField();
        Spinner<Integer> count = new Spinner<>(1, 120, 2);
        count.setEditable(true); count.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Profile> profile = new ComboBox<>();
        profile.getItems().setAll(context.profileService().listProfiles().stream().filter(Profile::active).toList());
        ComboBoxSupport.profiles(profile);
        profile.getItems().stream().findFirst().ifPresent(profile::setValue);
        ComboBox<Category> category = new ComboBox<>();
        category.getItems().setAll(context.categoryService().search(CategoryType.EXPENSE, null, false));
        ComboBoxSupport.categories(category);
        category.getItems().stream().findFirst().ifPresent(category::setValue);
        DatePicker first = DatePickerSupport.configure(new DatePicker(LocalDate.now()));
        TextArea notes = new TextArea();
        notes.setPrefRowCount(2);
        notes.setWrapText(true);
        Label error = new Label();
        error.setWrapText(true);
        error.getStyleClass().add("form-error");
        VBox preview = new VBox(5); preview.getStyleClass().add("installment-preview");
        Runnable updatePreview = () -> {
            preview.getChildren().clear();
            try {
                var installments = context.installmentPlanService().preview(description.getText(),
                        amount.getAmount(), count.getValue(), first.getValue());
                Label total = new Label(count.getValue() + " parcelas · total " + MoneyUtils.formatCents(
                        installments.stream().mapToLong(item -> item.amountCents()).sum()));
                total.getStyleClass().add("transaction-description");
                preview.getChildren().add(total);
                installments.stream().limit(3).forEach(item -> {
                    Label line = new Label(item.sequence() + "ª parcela · " + MoneyUtils.formatCents(item.amountCents())
                            + " · " + DATE.format(item.dueDate()));
                    line.getStyleClass().add("installment-meta"); preview.getChildren().add(line);
                });
                if (installments.size() > 3) preview.getChildren().add(new Label("+ " + (installments.size() - 3) + " parcelas"));
            } catch (RuntimeException exception) {
                Label hint = new Label("Informe valor, quantidade e vencimento para visualizar o resumo.");
                hint.getStyleClass().add("form-section-help"); preview.getChildren().add(hint);
            }
        };
        amount.textProperty().addListener((o, oldValue, newValue) -> updatePreview.run());
        count.valueProperty().addListener((o, oldValue, newValue) -> updatePreview.run());
        first.valueProperty().addListener((o, oldValue, newValue) -> updatePreview.run());
        updatePreview.run();

        VBox information = DialogSupport.section("Informações da compra", null,
                DialogSupport.field("Descrição", description, true),
                DialogSupport.columns(DialogSupport.field("Valor total", amount, true),
                        DialogSupport.field("Número de parcelas", count, true)),
                DialogSupport.columns(DialogSupport.field("Primeiro vencimento", first, true),
                        DialogSupport.field("Perfil", profile, true)),
                DialogSupport.field("Categoria", category, true),
                DialogSupport.field("Observações", notes, false));
        VBox summary = DialogSupport.section("Resumo", "Valores calculados pelo serviço de parcelamento.", preview);
        VBox content = DialogSupport.content(
                "As parcelas são compromissos independentes, sem vínculo com banco ou cartão.", information, summary, error);
        dialog.getDialogPane().setContent(DialogSupport.scrollRegion(content, 455));
        DialogSupport.prepare(dialog, list, 700, 570);
        ((Button) dialog.getDialogPane().lookupButton(saveType)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try {
                if(!FormValidationSupport.validate(error,
                        FormValidationSupport.requiredText(description,"Informe a descrição."),
                        FormValidationSupport.positiveMoney(amount,"Informe um valor maior que zero."),
                        FormValidationSupport.requiredDate(first,"Informe o primeiro vencimento."),
                        FormValidationSupport.requiredChoice(profile,"Selecione o perfil."),
                        FormValidationSupport.requiredChoice(category,"Selecione a categoria.")))return;
                context.installmentPlanService().create(new InstallmentPlanInput(description.getText(),
                        amount.getAmount(), count.getValue(),
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
        if(plan.active())actions.getItems().add(menu("Cancelar restantes", () -> cancel(plan)));
        else actions.getItems().add(menu("Excluir definitivamente",()->delete(plan)));
        HBox row = new HBox(10, info, spacer, status, money, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("transaction-row");
        return row;
    }

    private void details(InstallmentPlan plan) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Parcelas · " + plan.description());
        dialog.setHeaderText("Detalhes do parcelamento");
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));

        Label name = new Label(plan.description()); name.getStyleClass().add("detail-title");
        Label total = new Label(MoneyUtils.formatCents(plan.totalAmountCents())); total.getStyleClass().add("money-highlight");
        String statusClass = plan.pendingCount() == 0 ? "status-settled"
                : plan.cancelledCount() > 0 ? "status-cancelled" : "status-pending";
        Label status = DialogSupport.statusBadge(plan.calculatedStatus().displayName(), statusClass);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, name, spacer, status); header.setAlignment(Pos.CENTER_LEFT);
        Label progress = new Label(plan.paidCount() + " de " + plan.installmentCount() + " parcelas pagas · "
                + MoneyUtils.formatCents(plan.remainingAmountCents()) + " restante");
        progress.getStyleClass().add("form-section-help");
        VBox hero = new VBox(5, header, total, progress); hero.getStyleClass().add("detail-hero");

        VBox installments = new VBox(7); installments.getStyleClass().add("installment-list");
        for (var installment : context.installmentPlanService().installments(plan.id())) {
            boolean overdue = installment.status() == io.github.ryanoviski.hestia.domain.enums.TransactionStatus.PENDING
                    && installment.dueDate().isBefore(LocalDate.now());
            Label number = new Label(installment.number() + "/" + plan.installmentCount());
            number.getStyleClass().add("installment-number"); number.setMinWidth(48);
            Label due = new Label(DATE.format(installment.dueDate())); due.getStyleClass().add("installment-meta");
            Label value = new Label(MoneyUtils.formatCents(installment.plannedAmountCents()));
            value.getStyleClass().add("transaction-amount");
            Label badge = DialogSupport.statusBadge(overdue ? "Vencida"
                            : installment.status().displayName(TransactionType.EXPENSE),
                    "status-" + (overdue ? "overdue" : installment.status().name().toLowerCase()));
            Region rowSpacer = new Region(); HBox.setHgrow(rowSpacer, Priority.ALWAYS);
            HBox row = new HBox(10, number, due, rowSpacer, value, badge);
            row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("installment-row");
            installments.getChildren().add(row);
        }
        VBox listSection = DialogSupport.section("Parcelas",
                "Próximo vencimento: " + (plan.nextDueDate() == null ? "nenhum" : DATE.format(plan.nextDueDate())),
                DialogSupport.scrollRegion(installments, 300));
        VBox content = DialogSupport.content("Acompanhe o progresso sem expandir o diálogo além da área disponível.",
                hero, listSection);
        dialog.getDialogPane().setContent(content);
        DialogSupport.prepare(dialog, list, 700, 570);
        dialog.showAndWait();
    }

    private void cancel(InstallmentPlan plan) {
        if (DialogSupport.confirm(list, "Cancelar parcelas", "Cancelar parcelas restantes?",
                "Somente parcelas pendentes serão canceladas. Parcelas já pagas permanecerão no histórico.",
                "Cancelar restantes", true)) {
            context.installmentPlanService().cancelRemaining(plan.id());
            refresh();
        }
    }

    private void delete(InstallmentPlan plan){
        if(!DialogSupport.confirm(list,"Excluir parcelamento","Excluir definitivamente “"+plan.description()+"”?",
                "A definição e o vínculo das parcelas serão removidos. As movimentações continuarão no histórico financeiro.",
                "Excluir definitivamente",true))return;
        try{context.installmentPlanService().deleteInactive(plan.id());refresh();}
        catch(ValidationException exception){showError(exception.getMessage());}
        catch(RuntimeException exception){LOGGER.error("Could not delete installment plan {}",plan.id(),exception);showError("Não foi possível excluir o parcelamento. Tente novamente.");}
    }

    private void showError(String message){
        Alert alert=new Alert(Alert.AlertType.ERROR,message,ButtonType.OK);alert.setHeaderText("Não foi possível concluir");
        DialogSupport.prepare(alert,list,500,0);alert.showAndWait();
    }

    private MenuItem menu(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }
}
