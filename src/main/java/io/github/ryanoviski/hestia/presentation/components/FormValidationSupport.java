package io.github.ryanoviski.hestia.presentation.components;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class FormValidationSupport {
    private static final String CLEAR_LISTENER_KEY = FormValidationSupport.class.getName()+".clearListener";
    private FormValidationSupport() { }

    public static Rule requiredText(TextInputControl control, String message) {
        clearWhenChanged(control, control.textProperty());
        return new Rule(control, () -> control.getText() == null || control.getText().isBlank(), message);
    }

    public static Rule requiredChoice(ComboBox<?> control, String message) {
        clearWhenChanged(control, control.valueProperty());
        return new Rule(control, () -> control.getValue() == null, message);
    }

    public static Rule requiredDate(DatePicker control, String message) {
        clearWhenChanged(control, control.valueProperty());
        return new Rule(control, () -> control.getValue() == null, message);
    }

    public static Rule positiveMoney(MoneyTextField control, String message) {
        clearWhenChanged(control, control.textProperty());
        return new Rule(control, () -> control.getCents() <= 0, message);
    }

    public static boolean validate(Label summary, Rule... rules) {
        List<String> messages = new ArrayList<>();
        Node first = null;
        for (Rule rule : rules) {
            rule.control().getStyleClass().remove("field-invalid");
            if (!rule.invalid().getAsBoolean()) continue;
            if (!rule.control().getStyleClass().contains("field-invalid")) rule.control().getStyleClass().add("field-invalid");
            if (first == null) first = rule.control();
            messages.add(rule.message());
        }
        if (messages.isEmpty()) {
            summary.setText("");
            return true;
        }
        summary.setText("Preencha os campos obrigatórios para continuar:\n• " + String.join("\n• ", messages));
        summary.getStyleClass().removeAll("form-success", "feedback-text");
        if (!summary.getStyleClass().contains("form-error")) summary.getStyleClass().add("form-error");
        first.requestFocus();
        return false;
    }

    private static void clearWhenChanged(Node control, ObservableValue<?> value) {
        if(Boolean.TRUE.equals(control.getProperties().get(CLEAR_LISTENER_KEY)))return;
        value.addListener((observable, oldValue, newValue) -> control.getStyleClass().remove("field-invalid"));
        control.getProperties().put(CLEAR_LISTENER_KEY,Boolean.TRUE);
    }

    public record Rule(Node control, BooleanSupplier invalid, String message) { }
}
