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
    @FXML private ComboBox<CategoryType> filterType;
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

    @FXML private void initialize() {
        ComboBoxSupport.configure(filterType, CategoryType::displayName);
        ComboBoxSupport.configure(typeField, CategoryType::displayName);
        filterType.getItems().setAll(CategoryType.values());
        filterType.setPromptText("Todos os tipos");
        typeField.getItems().setAll(CategoryType.values());
        typeField.setValue(CategoryType.EXPENSE);
        searchField.textProperty().addListener((o, oldValue, newValue) -> refresh());
        filterType.valueProperty().addListener((o, oldValue, newValue) -> refresh());
        includeInactive.selectedProperty().addListener((o, oldValue, newValue) -> refresh());
    }

    public void configure(CategoryService service) { this.service = service; refresh(); }

    @FXML private void clearCategoryFilters() {
        searchField.clear(); filterType.setValue(null); includeInactive.setSelected(false);
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
        typeField.setValue(CategoryType.EXPENSE);
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
            var categories = service.search(filterType.getValue(), searchField.getText(), includeInactive.isSelected());
            categoryList.getChildren().clear();
            categories.forEach(category -> categoryList.getChildren().add(row(category)));
            emptyState.setVisible(categories.isEmpty());
            emptyState.setManaged(categories.isEmpty());
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
        Label details = new Label(category.type().displayName() + " · "
                + (category.standard() ? "Padrão" : "Personalizada") + " · "
                + (category.active() ? "Ativa" : "Inativa"));
        details.getStyleClass().add("profile-meta");
        VBox identity = new VBox(3, name, details);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        FlowPane actions = new FlowPane(8, 8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(12, color, identity, spacer, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-row");
        actions.getChildren().add(action("Editar", "secondary-button", () -> edit(category)));
        actions.getChildren().add(action(category.active()
                ? (category.standard() ? "Ocultar" : "Desativar") : "Reativar", "secondary-button", () -> toggle(category)));
        if (!category.standard()) {
            actions.getChildren().add(action("Excluir", "destructive-button", () -> delete(category)));
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

    private Button action(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> action.run());
        return button;
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
