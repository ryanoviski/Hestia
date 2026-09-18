package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.services.CategoryService;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
    @FXML private TextField colorField;
    @FXML private Label formMessage;
    @FXML private Button cancelEditButton;

    private CategoryService service;
    private Category editing;

    @FXML private void initialize() {
        filterType.getItems().setAll(CategoryType.values());
        filterType.setPromptText("Todos os tipos");
        typeField.getItems().setAll(CategoryType.values());
        typeField.setValue(CategoryType.EXPENSE);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> refresh());
        filterType.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        includeInactive.selectedProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    public void configure(CategoryService service) { this.service = service; refresh(); }

    @FXML private void clearCategoryFilters() { searchField.clear(); filterType.setValue(null); includeInactive.setSelected(false); }

    @FXML private void saveCategory() {
        try {
            if (editing == null) service.create(nameField.getText(), typeField.getValue(), colorField.getText());
            else service.update(editing.id(), nameField.getText(), typeField.getValue(), colorField.getText());
            showMessage(editing == null ? "Categoria criada com sucesso." : "Categoria atualizada com sucesso.", false);
            cancelEdit(); refresh();
        } catch (ValidationException exception) { showMessage(exception.getMessage(), true); }
        catch (RuntimeException exception) { LOGGER.error("Could not save category", exception); showMessage("Não foi possível salvar a categoria.", true); }
    }

    @FXML private void cancelEdit() {
        editing = null; formTitle.setText("Nova categoria"); nameField.clear(); colorField.clear();
        typeField.setDisable(false); typeField.setValue(CategoryType.EXPENSE); cancelEditButton.setVisible(false); cancelEditButton.setManaged(false);
    }

    private void edit(Category category) {
        editing = category; formTitle.setText("Editar categoria"); nameField.setText(category.name());
        typeField.setValue(category.type()); typeField.setDisable(false); colorField.setText(category.color());
        cancelEditButton.setVisible(true); cancelEditButton.setManaged(true);
    }

    private void refresh() {
        if (service == null) return;
        try {
            var categories = service.search(filterType.getValue(), searchField.getText(), includeInactive.isSelected());
            categoryList.getChildren().clear(); categories.forEach(category -> categoryList.getChildren().add(row(category)));
            emptyState.setVisible(categories.isEmpty()); emptyState.setManaged(categories.isEmpty());
        } catch (RuntimeException exception) { LOGGER.error("Could not load categories", exception); showMessage("Não foi possível carregar as categorias.", true); }
    }

    private HBox row(Category category) {
        Label color = new Label(); color.getStyleClass().add("category-color");
        if (category.color() != null) color.setStyle("-fx-background-color:" + category.color() + ";");
        Label name = new Label(category.name()); name.getStyleClass().add("profile-name");
        String meta = category.type().displayName() + " · " + (category.standard() ? "Padrão" : "Personalizada") + (category.active() ? "" : " · Inativa");
        Label details = new Label(meta); details.getStyleClass().add("profile-meta");
        VBox identity = new VBox(3, name, details);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, color, identity, spacer); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("profile-row");
        if (!category.standard()) {
            Button edit = new Button("Editar"); edit.getStyleClass().add("secondary-button"); edit.setOnAction(event -> edit(category));
            Button status = new Button(category.active() ? "Desativar" : "Reativar"); status.getStyleClass().add("secondary-button");
            status.setOnAction(event -> { try { service.setActive(category.id(), !category.active()); refresh(); } catch (ValidationException e) { showMessage(e.getMessage(), true); } });
            row.getChildren().addAll(edit, status);
        }
        return row;
    }

    private void showMessage(String message, boolean error) {
        formMessage.setText(message); formMessage.getStyleClass().removeAll("form-error", "form-success");
        formMessage.getStyleClass().add(error ? "form-error" : "form-success");
    }
}
