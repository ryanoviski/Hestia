package io.github.ryanoviski.hestia.presentation.components;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.Clipboard;

import java.math.BigDecimal;

/** Monetary input that keeps cents as its source of truth. */
public final class MoneyTextField extends TextField {
    private final MoneyInputModel model;

    public MoneyTextField() { this(0); }

    public MoneyTextField(long initialCents) {
        model = new MoneyInputModel(initialCents);
        getStyleClass().add("money-field");
        setText(model.formatted());
        setAccessibleHelp("Valor em reais; digite os números começando pelos centavos.");
        addEventFilter(KeyEvent.KEY_TYPED, this::typed);
        addEventFilter(KeyEvent.KEY_PRESSED, this::pressed);
        focusedProperty().addListener((observable, oldValue, focused) -> { if (focused) selectAll(); });
    }

    public BigDecimal getAmount() { return model.amount(); }

    public long getCents() { return model.cents(); }

    @Override public void paste() {
        String text = Clipboard.getSystemClipboard().getString();
        try {
            model.replaceFromPaste(text);
            render();
        } catch (ValidationException exception) {
            invalid(exception.getMessage());
        }
    }

    private void typed(KeyEvent event) {
        String character = event.getCharacter();
        if (character != null && character.length() == 1 && Character.isDigit(character.charAt(0))) {
            try {
                if(getSelection().getLength()>0)model.clear();
                model.appendDigit(Character.digit(character.charAt(0), 10));
                render();
            } catch (ValidationException exception) {
                invalid(exception.getMessage());
            }
        }
        event.consume();
    }

    private void pressed(KeyEvent event) {
        if (event.getCode() == KeyCode.BACK_SPACE) {
            if(getSelection().getLength()>0)model.clear();else model.backspace();
            render();
            event.consume();
        } else if (event.getCode() == KeyCode.DELETE) {
            model.clear();
            render();
            event.consume();
        }
    }

    private void render() {
        setText(model.formatted());
        positionCaret(getText().length());
        getStyleClass().remove("field-invalid");
        setTooltip(null);
    }

    private void invalid(String message) {
        if (!getStyleClass().contains("field-invalid")) getStyleClass().add("field-invalid");
        setTooltip(new Tooltip(message));
    }
}
