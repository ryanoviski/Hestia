package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.services.CategoryService;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.presentation.components.ColorPalette;
import io.github.ryanoviski.hestia.presentation.components.ComboBoxSupport;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CategoriesController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CategoriesController.class);
    @FXML private TextField searchField;
    @FXML private Button expenseTab;
    @FXML private Button incomeTab;
    @FXML private CheckBox includeInactive;
    @FXML private VBox categoryList;
    @FXML private Label emptyState;
    @FXML private Label formTitle;
    @FXML private TextField nameField;
    @FXML private ComboBox<CategoryType> typeField;
    @FXML private ColorPalette colorPalette;
    @FXML private Label formMessage;
    @FXML private Button cancelEditButton;

    private CategoryService service;
    private Category editing;
    private CategoryType selectedType = CategoryType.EXPENSE;

    @FXML private void initialize() {
        ComboBoxSupport.configure(typeField, CategoryType::displayName);
        typeField.getItems().setAll(CategoryType.values());
        typeField.setValue(CategoryType.EXPENSE);
        searchField.textProperty().addListener((o, oldValue, newValue) -> refresh());
        includeInactive.selectedProperty().addListener((o, oldValue, newValue) -> refresh());
    }

    public void configure(CategoryService service) { this.service = service; refresh(); }

    @FXML private void showExpenses() { selectType(CategoryType.EXPENSE); }
    @FXML private void showIncome() { selectType(CategoryType.INCOME); }

    private void selectType(CategoryType type) {
        selectedType = type;
        if (editing != null && editing.type() != type) cancelEdit();
        expenseTab.getStyleClass().remove("segment-selected");
        incomeTab.getStyleClass().remove("segment-selected");
        (type == CategoryType.EXPENSE ? expenseTab : incomeTab).getStyleClass().add("segment-selected");
        if (editing == null) typeField.setValue(type);
        refresh();
    }

    @FXML private void saveCategory() {
        try {
            boolean creating = editing == null;
            if (creating) service.create(nameField.getText(), typeField.getValue(), colorPalette.getSelectedColor());
            else service.update(editing.id(), nameField.getText(), typeField.getValue(), colorPalette.getSelectedColor());
            cancelEdit();
            showMessage(creating ? "Categoria criada com sucesso." : "Categoria atualizada com sucesso.", false);
            refresh();
        } catch (ValidationException exception) {
            showMessage(exception.getMessage(), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not save category", exception);
            showMessage("Não foi possível salvar a categoria.", true);
        }
    }

    @FXML private void cancelEdit() {
        editing = null;
        formTitle.setText("Nova categoria");
        nameField.clear();
        colorPalette.setSelectedColor(null);
        typeField.setDisable(false);
        typeField.setValue(selectedType);
        cancelEditButton.setVisible(false);
        cancelEditButton.setManaged(false);
    }

    private void edit(Category category) {
        editing = category;
        formTitle.setText("Editar categoria");
        nameField.setText(category.name());
        typeField.setValue(category.type());
        typeField.setDisable(category.standard());
        colorPalette.setSelectedColor(category.color());
        cancelEditButton.setVisible(true);
        cancelEditButton.setManaged(true);
        nameField.requestFocus();
    }

    private void refresh() {
        if (service == null) return;
        try {
            var categories = service.search(selectedType, searchField.getText(), includeInactive.isSelected());
            categoryList.getChildren().clear();
            categories.forEach(category -> categoryList.getChildren().add(row(category)));
            emptyState.setVisible(categories.isEmpty());
            emptyState.setManaged(categories.isEmpty());
            if (categories.isEmpty()) categoryList.getChildren().add(emptyState);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not load categories", exception);
            showMessage("Não foi possível carregar as categorias.", true);
        }
    }

    private HBox row(Category category) {
        Label color = new Label();
        color.getStyleClass().add("category-color");
        if (category.color() != null) color.setStyle("-fx-background-color: " + category.color() + ";");
        Label name = new Label(category.name());
        name.getStyleClass().add("profile-name");
        Label details = new Label((category.active() ? "Ativa" : "Inativa") + " · "
                + (category.standard() ? "Categoria padrão" : "Criada por você"));
        details.getStyleClass().add("profile-meta");
        VBox identity = new VBox(3, name, details);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        MenuButton actions = new MenuButton("Ações");
        actions.getStyleClass().add("ghost-button");
        HBox row = new HBox(12, color, identity, spacer, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-row");
        actions.getItems().add(menu("Editar", () -> edit(category)));
        actions.getItems().add(menu(category.active()
                ? (category.standard() ? "Ocultar" : "Desativar") : "Reativar", () -> toggle(category)));
        if (!category.standard()) {
            actions.getItems().add(menu("Excluir", () -> delete(category)));
        }
        return row;
    }

    private void toggle(Category category) {
        try {
            service.setActive(category.id(), !category.active());
            showMessage(category.active()
                    ? (category.standard() ? "Categoria padrão ocultada para este grupo." : "Categoria desativada.")
                    : "Categoria reativada.", false);
            refresh();
        } catch (ValidationException exception) {
            showMessage(exception.getMessage(), true);
        }
    }

    private MenuItem menu(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private void delete(Category category) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Excluir a categoria \"" + category.name() + "\"? Esta ação só será permitida se ela nunca tiver sido usada.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setTitle("Excluir categoria");
        confirmation.setHeaderText("Confirme a exclusão permanente");
        ThemeManager.apply(confirmation);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            service.delete(category.id());
            if (editing != null && editing.id().equals(category.id())) cancelEdit();
            showMessage("Categoria excluída.", false);
            refresh();
        } catch (ValidationException exception) {
            showMessage(exception.getMessage(), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not delete category {}", category.id(), exception);
            showMessage("Não foi possível excluir a categoria.", true);
        }
    }

    private void showMessage(String message, boolean error) {
        formMessage.setText(message);
        formMessage.getStyleClass().removeAll("form-error", "form-success");
        formMessage.getStyleClass().add(error ? "form-error" : "form-success");
    }
}
