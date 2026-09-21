package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.services.DashboardService;
import io.github.ryanoviski.hestia.presentation.components.MonthYearPicker;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class DashboardController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardController.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");
    @FXML private MonthYearPicker monthPicker;
    @FXML private Label receivedIncome;
    @FXML private Label expectedIncome;
    @FXML private Label paidExpenses;
    @FXML private Label pendingExpenses;
    @FXML private Label overdueExpenses;
    @FXML private Label realizedResult;
    @FXML private Label projectedResult;
    @FXML private VBox upcomingList;
    @FXML private VBox upcomingEmpty;
    @FXML private VBox categoryTotals;
    @FXML private VBox categoryEmpty;
    private DashboardService service;

    public void configure(DashboardService service) {
        this.service = service;
        monthPicker.setValue(YearMonth.now());
        refresh();
    }

    @FXML private void previousMonth() { monthPicker.setValue(monthPicker.getValue().minusMonths(1)); refresh(); }
    @FXML private void nextMonth() { monthPicker.setValue(monthPicker.getValue().plusMonths(1)); refresh(); }

    @FXML private void refresh() {
        if (service == null) return;
        try {
            var summary = service.summary(monthPicker.getValue());
            receivedIncome.setText(MoneyUtils.format(summary.receivedIncome()));
            expectedIncome.setText(MoneyUtils.format(summary.expectedIncome()) + " ainda previstas");
            paidExpenses.setText(MoneyUtils.format(summary.paidExpenses()));
            pendingExpenses.setText(MoneyUtils.format(summary.pendingExpenses()) + " ainda pendentes");
            overdueExpenses.setText(MoneyUtils.format(summary.overdueExpenses()));
            realizedResult.setText(MoneyUtils.format(summary.realizedResult()));
            projectedResult.setText("Projeção do período: " + MoneyUtils.format(summary.projectedResult()));
            styleResult(summary.realizedResult());
            upcomingList.getChildren().clear();
            summary.upcomingDue().forEach(item -> {
                Label day = new Label(DATE.format(item.dueDate())); day.getStyleClass().add("due-date-box");
                Label description = new Label(item.description()); description.getStyleClass().add("transaction-description");
                Label meta = new Label(item.profileName() + " · " + item.categoryName()); meta.getStyleClass().add("section-caption");
                VBox identity = new VBox(2, description, meta);
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                Label amount = new Label(MoneyUtils.formatCents(item.amountCents())); amount.getStyleClass().add("transaction-amount");
                HBox row = new HBox(10, day, identity, spacer, amount); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("compact-row");
                upcomingList.getChildren().add(row);
            });
            toggle(upcomingEmpty, summary.upcomingDue().isEmpty());
            categoryTotals.getChildren().clear();
            BigDecimal max = summary.expensesByCategory().values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
            summary.expensesByCategory().entrySet().stream().limit(5).forEach(entry -> {
                Label name = new Label(entry.getKey()); Label value = new Label(MoneyUtils.format(entry.getValue()));
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                ProgressBar bar = new ProgressBar(entry.getValue().divide(max, 4, java.math.RoundingMode.HALF_UP).doubleValue());
                bar.setMaxWidth(Double.MAX_VALUE); bar.getStyleClass().add("distribution-bar");
                categoryTotals.getChildren().add(new VBox(4, new HBox(8, name, spacer, value), bar));
            });
            toggle(categoryEmpty, summary.expensesByCategory().isEmpty());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not update dashboard", exception);
            Alert alert = new Alert(Alert.AlertType.ERROR, "Não foi possível atualizar o painel.", ButtonType.OK);
            alert.setHeaderText("Painel indisponível"); ThemeManager.apply(alert); alert.showAndWait();
        }
    }

    private void styleResult(BigDecimal value) {
        realizedResult.getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");
        realizedResult.getStyleClass().add(value.signum() > 0 ? "amount-positive" : value.signum() < 0 ? "amount-negative" : "amount-neutral");
    }
    private void toggle(VBox node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }
}
