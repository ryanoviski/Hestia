package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.services.DashboardService;
import io.github.ryanoviski.hestia.presentation.components.MonthYearPicker;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class DashboardController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardController.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    @FXML private MonthYearPicker monthPicker;
    @FXML private Label receivedIncome;
    @FXML private Label expectedIncome;
    @FXML private Label paidExpenses;
    @FXML private Label pendingExpenses;
    @FXML private Label overdueExpenses;
    @FXML private Label realizedResult;
    @FXML private Label projectedResult;
    @FXML private VBox upcomingList;
    @FXML private Label upcomingEmpty;
    @FXML private VBox categoryTotals;
    @FXML private Label categoryEmpty;
    private DashboardService service;

    public void configure(DashboardService service) {
        this.service = service;
        monthPicker.setValue(YearMonth.now());
        refresh();
    }

    @FXML private void refresh() {
        if(service==null)return;
        try {
            YearMonth month = monthPicker.getValue();
            var summary=service.summary(month);
            receivedIncome.setText(MoneyUtils.format(summary.receivedIncome())); expectedIncome.setText("Previstas: "+MoneyUtils.format(summary.expectedIncome()));
            paidExpenses.setText(MoneyUtils.format(summary.paidExpenses())); pendingExpenses.setText("Pendentes: "+MoneyUtils.format(summary.pendingExpenses()));
            overdueExpenses.setText(MoneyUtils.format(summary.overdueExpenses())); realizedResult.setText(MoneyUtils.format(summary.realizedResult()));
            projectedResult.setText("Projetado: "+MoneyUtils.format(summary.projectedResult()));
            upcomingList.getChildren().clear(); summary.upcomingDue().forEach(item->{Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);upcomingList.getChildren().add(new HBox(8,new Label(item.description()+" · "+item.profileName()+" · "+DATE.format(item.dueDate())),spacer,new Label(MoneyUtils.formatCents(item.amountCents()))));});
            upcomingEmpty.setVisible(summary.upcomingDue().isEmpty()); upcomingEmpty.setManaged(summary.upcomingDue().isEmpty());
            categoryTotals.getChildren().clear(); summary.expensesByCategory().forEach((name,value)->{Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);categoryTotals.getChildren().add(new HBox(8,new Label(name),spacer,new Label(MoneyUtils.format(value))));});
            categoryEmpty.setVisible(summary.expensesByCategory().isEmpty()); categoryEmpty.setManaged(summary.expensesByCategory().isEmpty());
        } catch(RuntimeException exception) { LOGGER.error("Could not update dashboard", exception); showError("Não foi possível atualizar o painel."); }
    }
    private void showError(String message){Alert alert=new Alert(Alert.AlertType.ERROR,message,ButtonType.OK);ThemeManager.apply(alert);alert.showAndWait();}
}
