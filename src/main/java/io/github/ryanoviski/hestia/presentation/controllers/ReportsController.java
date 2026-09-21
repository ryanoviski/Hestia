package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.dto.MonthlyReport;
import io.github.ryanoviski.hestia.config.ApplicationContext;
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
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public final class ReportsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportsController.class);
    private static final DateTimeFormatter FILE_PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("MMMM 'de' yyyy",
            Locale.forLanguageTag("pt-BR"));

    @FXML private MonthYearPicker monthPicker;
    @FXML private Label periodTitle;
    @FXML private Label totalIncome;
    @FXML private Label totalExpenses;
    @FXML private Label projectedResult;
    @FXML private Label receivedIncome;
    @FXML private Label expectedIncome;
    @FXML private Label paidExpenses;
    @FXML private Label pendingExpenses;
    @FXML private Label overdueExpenses;
    @FXML private Label incomeComparison;
    @FXML private Label expenseComparison;
    @FXML private Label resultComparison;
    @FXML private VBox categoryList;
    @FXML private VBox profileList;
    @FXML private Label feedback;

    private ApplicationContext context;
    private MonthlyReport report;

    public void configure(ApplicationContext context) {
        this.context = context;
        monthPicker.setValue(YearMonth.now());
        refresh();
    }

    @FXML private void previousMonth() {
        monthPicker.setValue(monthPicker.getValue().minusMonths(1));
        refresh();
    }

    @FXML private void nextMonth() {
        monthPicker.setValue(monthPicker.getValue().plusMonths(1));
        refresh();
    }

    @FXML private void refresh() {
        if (context == null) return;
        try {
            report = context.reportService().monthly(monthPicker.getValue());
            String title = PERIOD.format(report.period());
            periodTitle.setText(Character.toUpperCase(title.charAt(0)) + title.substring(1));
            totalIncome.setText(MoneyUtils.format(report.totalIncome()));
            totalExpenses.setText(MoneyUtils.format(report.totalExpenses()));
            projectedResult.setText(MoneyUtils.format(report.current().projectedResult()));
            projectedResult.getStyleClass().removeAll("amount-positive", "amount-negative");
            projectedResult.getStyleClass().add(report.current().projectedResult().signum() < 0
                    ? "amount-negative" : "amount-positive");
            receivedIncome.setText(MoneyUtils.format(report.current().receivedIncome()));
            expectedIncome.setText(MoneyUtils.format(report.current().expectedIncome()));
            paidExpenses.setText(MoneyUtils.format(report.current().paidExpenses()));
            pendingExpenses.setText(MoneyUtils.format(report.current().pendingExpenses()));
            overdueExpenses.setText(MoneyUtils.format(report.current().overdueExpenses()));
            comparison(incomeComparison, report.incomeChange(), true);
            comparison(expenseComparison, report.expenseChange(), false);
            comparison(resultComparison, report.resultChange(), true);
            fillDistribution(categoryList, report.current().expensesByCategory());
            fillDistribution(profileList, report.expensesByProfile());
            feedback.setText("");
        } catch (RuntimeException exception) {
            LOGGER.error("Could not generate monthly report", exception);
            showError("Não foi possível gerar o relatório deste período.");
        }
    }

    @FXML private void exportPdf() {
        if (report == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar relatório financeiro");
        chooser.setInitialFileName("hestia-relatorio-" + FILE_PERIOD.format(report.period()) + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        File selected = chooser.showSaveDialog(feedback.getScene().getWindow());
        if (selected == null) return;
        try {
            var file = context.reportPdfService().export(report, selected.toPath());
            feedback.setText("PDF exportado em " + file);
            feedback.getStyleClass().removeAll("form-error", "form-success");
            feedback.getStyleClass().add("form-success");
        } catch (RuntimeException exception) {
            LOGGER.error("Could not export monthly report", exception);
            showError(exception.getMessage());
        }
    }

    private void comparison(Label label, BigDecimal change, boolean increaseIsPositive) {
        label.setText((change.signum() > 0 ? "+" : "") + MoneyUtils.format(change) + " em relação ao mês anterior");
        label.getStyleClass().removeAll("amount-positive", "amount-negative", "amount-neutral");
        if (change.signum() == 0) label.getStyleClass().add("amount-neutral");
        else if ((change.signum() > 0) == increaseIsPositive) label.getStyleClass().add("amount-positive");
        else label.getStyleClass().add("amount-negative");
    }

    private void fillDistribution(VBox container, Map<String, BigDecimal> values) {
        container.getChildren().clear();
        if (values.isEmpty()) {
            Label empty = new Label("Nenhuma despesa registrada neste período.");
            empty.getStyleClass().add("empty-description");
            container.getChildren().add(empty);
            return;
        }
        BigDecimal maximum = values.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        values.forEach((name, value) -> {
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("distribution-name");
            Label amount = new Label(MoneyUtils.format(value));
            amount.getStyleClass().add("distribution-value");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox heading = new HBox(8, nameLabel, spacer, amount);
            heading.setAlignment(Pos.CENTER_LEFT);
            ProgressBar bar = new ProgressBar(value.divide(maximum, 4, java.math.RoundingMode.HALF_UP).doubleValue());
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.getStyleClass().add("distribution-bar");
            container.getChildren().add(new VBox(5, heading, bar));
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                message == null ? "Não foi possível concluir a operação." : message, ButtonType.OK);
        alert.setHeaderText("Relatório indisponível");
        ThemeManager.apply(alert);
        alert.showAndWait();
    }
}
