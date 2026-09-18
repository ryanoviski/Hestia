package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
public final class MainController {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainController.class);

    @FXML private StackPane contentArea;
    @FXML private Label pageTitle;
    @FXML private VBox navigation;

    private ApplicationContext context;

    public void configure(ApplicationContext context) {
        this.context = context;
        showDashboard();
    }

    @FXML
    private void navigate(javafx.event.ActionEvent event) {
        Button button = (Button) event.getSource();
        String destination = (String) button.getUserData();
        select(button);
        switch (destination) {
            case "dashboard" -> showDashboard();
            case "profiles" -> showProfiles();
            case "categories" -> showCategories();
            case "transactions" -> showTransactions("Movimentações", null, false);
            case "income" -> showTransactions("Receitas", TransactionType.INCOME, false);
            case "bills" -> showTransactions("Contas a pagar", TransactionType.EXPENSE, true);
            case "recurring" -> showRecurring();
            case "installments" -> showInstallments();
            case "calendar" -> showCalendar();
            default -> showPlaceholder(button.getText().trim());
        }
    }

    private void showDashboard() {
        pageTitle.setText("Painel");
        loadContent("/fxml/dashboard-view.fxml", loader -> {
            DashboardController controller = loader.getController();
            controller.configure(context.dashboardService());
        });
        selectByDestination("dashboard");
    }

    private void showCategories() {
        pageTitle.setText("Categorias");
        loadContent("/fxml/categories-view.fxml", loader -> {
            CategoriesController controller = loader.getController();
            controller.configure(context.categoryService());
        });
    }

    private void showTransactions(String title, TransactionType fixedType, boolean payableMode) {
        pageTitle.setText(title);
        loadContent("/fxml/transactions-view.fxml", loader -> {
            TransactionsController controller = loader.getController();
            controller.configure(context, fixedType, payableMode);
        });
    }

    private void showProfiles() {
        pageTitle.setText("Perfis");
        loadContent("/fxml/profiles-view.fxml", loader -> {
            ProfilesController controller = loader.getController();
            controller.configure(context.profileService());
        });
    }

    private void showRecurring() { pageTitle.setText("Recorrências"); loadContent("/fxml/recurring-expenses-view.fxml", loader -> ((RecurringExpensesController)loader.getController()).configure(context)); }
    private void showInstallments() { pageTitle.setText("Parcelamentos"); loadContent("/fxml/installment-plans-view.fxml", loader -> ((InstallmentPlansController)loader.getController()).configure(context)); }
    private void showCalendar() { pageTitle.setText("Calendário"); loadContent("/fxml/calendar-view.fxml", loader -> ((CalendarController)loader.getController()).configure(context)); }

    private void showPlaceholder(String title) {
        pageTitle.setText(title);
        loadContent("/fxml/placeholder-view.fxml", loader -> {
            PlaceholderController controller = loader.getController();
            controller.setModuleName(title);
        });
    }

    private void loadContent(String resource, LoaderConfigurer configurer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
            Node node = loader.load();
            if (configurer != null) {
                configurer.configure(loader);
            }
            contentArea.getChildren().setAll(node);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not load view {}", resource, exception);
            contentArea.getChildren().setAll(new Label("Não foi possível carregar esta tela."));
        }
    }

    private void select(Button selected) {
        navigation.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .forEach(button -> button.getStyleClass().remove("nav-button-active"));
        if (!selected.getStyleClass().contains("nav-button-active")) {
            selected.getStyleClass().add("nav-button-active");
        }
    }

    private void selectByDestination(String destination) {
        navigation.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> destination.equals(button.getUserData()))
                .findFirst().ifPresent(this::select);
    }

    @FunctionalInterface
    private interface LoaderConfigurer {
        void configure(FXMLLoader loader);
    }
}
