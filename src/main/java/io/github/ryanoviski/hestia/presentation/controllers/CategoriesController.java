package io.github.ryanoviski.hestia.presentation.controllers;

import io.github.ryanoviski.hestia.application.services.CategoryService;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.presentation.components.ColorPalette;
import io.github.ryanoviski.hestia.presentation.components.ComboBoxSupport;
import io.github.ryanoviski.hestia.presentation.components.DialogSupport;
import io.github.ryanoviski.hestia.presentation.components.FormValidationSupport;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
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
    @FXML private Label formMessage;

    private CategoryService service;
    private CategoryType selectedType = CategoryType.EXPENSE;

    @FXML private void initialize() {
        searchField.textProperty().addListener((o, oldValue, newValue) -> refresh());
        includeInactive.selectedProperty().addListener((o, oldValue, newValue) -> refresh());
    }

    public void configure(CategoryService service) { this.service = service; refresh(); }

    @FXML private void showExpenses() { selectType(CategoryType.EXPENSE); }
    @FXML private void showIncome() { selectType(CategoryType.INCOME); }

    private void selectType(CategoryType type) {
        selectedType = type;
        expenseTab.getStyleClass().remove("segment-selected");
        incomeTab.getStyleClass().remove("segment-selected");
        (type == CategoryType.EXPENSE ? expenseTab : incomeTab).getStyleClass().add("segment-selected");
        refresh();
    }

    @FXML private void createCategory() { showCategoryDialog(null); }

    private void edit(Category category) { showCategoryDialog(category); }

    private void showCategoryDialog(Category category) {
        boolean creating = category == null;
        TextField nameField = new TextField(creating ? "" : category.name());
        nameField.setPromptText("Ex.: Pets");
        ComboBox<CategoryType> typeField = new ComboBox<>();
        ComboBoxSupport.configure(typeField, CategoryType::displayName);
        typeField.getItems().setAll(CategoryType.values());
        typeField.setValue(creating ? selectedType : category.type());
        typeField.setDisable(!creating && category.standard());
        typeField.setMaxWidth(Double.MAX_VALUE);
        ColorPalette colorPalette = new ColorPalette();
        colorPalette.setSelectedColor(creating ? null : category.color());
        colorPalette.setMaxWidth(Double.MAX_VALUE);
        Label message = new Label();
        message.setWrapText(true);
        message.getStyleClass().add("feedback-text");
        nameField.textProperty().addListener((observable, oldValue, newValue) ->
                nameField.getStyleClass().remove("field-invalid"));

        Label hint = new Label("Categorias padrão podem ser personalizadas sem alterar a base do Hestia.");
        hint.setWrapText(true);
        hint.getStyleClass().add("field-hint");
        VBox fields = DialogSupport.section("Identificação",
                "Use um nome curto e uma cor que facilitem a leitura dos lançamentos.",
                DialogSupport.field("Nome", nameField, true),
                DialogSupport.field("Tipo", typeField, true),
                DialogSupport.field("Cor de identificação", colorPalette, false), hint, message);

        Dialog<ButtonType> dialog = new Dialog<>();
        String title = creating ? "Nova categoria" : "Editar categoria";
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        ButtonType saveType = DialogSupport.primaryAction(creating ? "Criar categoria" : "Salvar alterações");
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, saveType);
        dialog.getDialogPane().setContent(DialogSupport.content(
                creating ? "Adicione uma categoria para organizar suas movimentações."
                        : "Atualize os dados da categoria selecionada.", fields));
        DialogSupport.prepare(dialog, categoryList, 540, 0);

        boolean[] saved = {false};
        CategoryType[] savedType = {typeField.getValue()};
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if(!FormValidationSupport.validate(message,
                        FormValidationSupport.requiredText(nameField,"Informe o nome."),
                        FormValidationSupport.requiredChoice(typeField,"Selecione o tipo."))){event.consume();return;}
                if (creating) service.create(nameField.getText(), typeField.getValue(), colorPalette.getSelectedColor());
                else service.update(category.id(), nameField.getText(), typeField.getValue(), colorPalette.getSelectedColor());
                saved[0] = true;
                savedType[0] = typeField.getValue();
            } catch (ValidationException exception) {
                event.consume();
                if (nameField.getText() == null || nameField.getText().isBlank()) {
                    if (!nameField.getStyleClass().contains("field-invalid")) nameField.getStyleClass().add("field-invalid");
                    nameField.requestFocus();
                }
                dialogMessage(message, exception.getMessage());
            } catch (RuntimeException exception) {
                event.consume();
                LOGGER.error("Could not save category", exception);
                dialogMessage(message, "Não foi possível salvar a categoria.");
            }
        });
        dialog.setOnShown(event -> nameField.requestFocus());
        dialog.showAndWait();
        if (saved[0]) {
            selectType(savedType[0]);
            showMessage(creating ? "Categoria criada com sucesso." : "Categoria atualizada com sucesso.", false);
        }
    }

    private void dialogMessage(Label message, String text) {
        message.setText(text);
        message.getStyleClass().removeAll("form-error", "form-success");
        message.getStyleClass().add("form-error");
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
        if (!DialogSupport.confirm(categoryList, "Excluir categoria", "Excluir “" + category.name() + "”?",
                "A exclusão só será permitida quando a categoria nunca tiver sido usada.",
                "Excluir categoria", true)) return;
        try {
            service.delete(category.id());
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
