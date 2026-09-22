package io.github.ryanoviski.hestia.presentation.components;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public final class MonthYearPicker extends Button {
    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PORTUGUESE);
    private final ObjectProperty<YearMonth> value = new SimpleObjectProperty<>(YearMonth.now());

    public MonthYearPicker() {
        getStyleClass().add("month-year-picker");
        setAccessibleText("Selecionar mês e ano");
        setMinWidth(178);
        setMaxWidth(Double.MAX_VALUE);
        value.addListener((observable, oldValue, newValue) -> updateText());
        setOnAction(event -> showPicker());
        updateText();
    }

    public YearMonth getValue() {
        return value.get();
    }

    public void setValue(YearMonth month) {
        value.set(month == null ? YearMonth.now() : month);
    }

    public ObjectProperty<YearMonth> valueProperty() {
        return value;
    }

    public String getDisplayText() {
        return getText();
    }

    public YearMonth moveYear(int years) {
        YearMonth moved = getValue().plusYears(years);
        setValue(moved);
        return moved;
    }

    private void updateText() {
        String formatted = DISPLAY.format(getValue());
        setText(Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1));
    }

    private void showPicker() {
        Dialog<YearMonth> dialog = new Dialog<>();
        dialog.setTitle("Selecionar mês");
        dialog.setHeaderText("Escolha o mês e o ano");
        ButtonType confirm = DialogSupport.primaryAction("Selecionar");
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancel, confirm);

        int[] selectedYear = {getValue().getYear()};
        int[] selectedMonth = {getValue().getMonthValue()};
        Label yearLabel = new Label(Integer.toString(selectedYear[0]));
        yearLabel.getStyleClass().add("month-picker-year");
        Button previous = new Button("Ano anterior");
        Button next = new Button("Próximo ano");
        previous.getStyleClass().add("secondary-button");
        next.getStyleClass().add("secondary-button");
        Region yearSpacer = new Region();
        HBox.setHgrow(yearSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, previous, yearSpacer, yearLabel, new Region(), next);
        HBox.setHgrow(header.getChildren().get(3), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER);

        ToggleGroup months = new ToggleGroup();
        GridPane monthGrid = new GridPane();
        monthGrid.setHgap(8);
        monthGrid.setVgap(8);
        Runnable rebuild = () -> {
            monthGrid.getChildren().clear();
            months.getToggles().clear();
            yearLabel.setText(Integer.toString(selectedYear[0]));
            for (Month month : Month.values()) {
                String shortName = month.getDisplayName(TextStyle.SHORT, PORTUGUESE)
                        .replace(".", "");
                ToggleButton button = new ToggleButton(Character.toUpperCase(shortName.charAt(0))
                        + shortName.substring(1));
                button.setAccessibleText(month.getDisplayName(TextStyle.FULL, PORTUGUESE));
                button.setUserData(month.getValue());
                button.setToggleGroup(months);
                button.getStyleClass().add("month-option");
                button.setMaxWidth(Double.MAX_VALUE);
                if (month.getValue() == selectedMonth[0]) {
                    button.setSelected(true);
                }
                monthGrid.add(button, (month.getValue() - 1) % 3, (month.getValue() - 1) / 3);
                GridPane.setHgrow(button, Priority.ALWAYS);
            }
        };
        months.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                selectedMonth[0] = (Integer) newValue.getUserData();
            }
        });
        previous.setOnAction(event -> {
            selectedYear[0]--;
            rebuild.run();
        });
        next.setOnAction(event -> {
            selectedYear[0]++;
            rebuild.run();
        });

        Button current = new Button("Ir para o mês atual");
        current.getStyleClass().add("link-button");
        current.setOnAction(event -> {
            YearMonth now = YearMonth.now();
            selectedYear[0] = now.getYear();
            selectedMonth[0] = now.getMonthValue();
            rebuild.run();
        });
        VBox content = new VBox(14, header, monthGrid, current);
        content.getStyleClass().add("month-picker-content");
        rebuild.run();
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == confirm
                ? YearMonth.of(selectedYear[0], selectedMonth[0]) : null);
        DialogSupport.prepare(dialog, this, 480, 430);
        dialog.showAndWait().ifPresent(this::setValue);
    }
}
