package io.github.ryanoviski.hestia.presentation;

import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.presentation.components.ColorPalette;
import io.github.ryanoviski.hestia.presentation.components.ComboBoxSupport;
import io.github.ryanoviski.hestia.presentation.components.DatePickerSupport;
import io.github.ryanoviski.hestia.presentation.components.DialogSupport;
import io.github.ryanoviski.hestia.presentation.components.MonthYearPicker;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class JavaFxViewSmokeTest {
    @BeforeAll static void startJavaFx() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                started.complete(null);
            });
        } catch (IllegalStateException alreadyStarted) {
            started.complete(null);
        }
        started.get(10, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"main-view.fxml", "dashboard-view.fxml", "profiles-view.fxml",
            "categories-view.fxml", "transactions-view.fxml", "placeholder-view.fxml",
            "recurring-expenses-view.fxml", "installment-plans-view.fxml", "calendar-view.fxml",
            "documents-view.fxml", "settings-view.fxml", "reports-view.fxml"})
    void loadsViewWithItsController(String resource) throws Exception {
        CompletableFuture<Parent> loaded = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { loaded.complete(new FXMLLoader(getClass().getResource("/fxml/" + resource)).load()); }
            catch (Exception exception) { loaded.completeExceptionally(exception); }
        });
        assertThat(loaded.get(10, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void reusableControlsExposeReadableValuesAndTheme() throws Exception {
        CompletableFuture<Void> checked = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                MonthYearPicker month = new MonthYearPicker();
                month.setValue(YearMonth.of(2026, 9));
                assertThat(month.getDisplayText()).isEqualTo("Setembro de 2026");
                assertThat(month.moveYear(1)).isEqualTo(YearMonth.of(2027, 9));

                ColorPalette palette = new ColorPalette();
                palette.setSelectedColor("#3F6FA0");
                assertThat(palette.getSelectedColor()).isEqualTo("#3F6FA0");
                assertThat(palette.getChildren()).hasSize(ColorPalette.COLORS.size());
                palette.setSelectedColor("#123456");
                assertThat(palette.getSelectedColor()).isEqualTo("#123456");
                assertThat(palette.getChildren()).hasSize(ColorPalette.COLORS.size() + 1);

                Profile profile = new Profile(1L, 1L, "Ana", ProfileType.PERSON, null, true,
                        Instant.EPOCH, Instant.EPOCH);
                ComboBox<Profile> profiles = new ComboBox<>();
                ComboBoxSupport.profiles(profiles);
                profiles.getItems().add(profile);
                profiles.setValue(profile);
                assertThat(profiles.getConverter().toString(profile)).isEqualTo("Ana");
                assertThat(profiles.getConverter().toString(profile)).doesNotContain("Profile[");

                Category category = new Category(1L, 1L, "Moradia", CategoryType.EXPENSE, null,
                        null, true, Instant.EPOCH, Instant.EPOCH);
                ComboBox<Category> categories = new ComboBox<>();
                ComboBoxSupport.categories(categories);
                assertThat(categories.getConverter().toString(category)).isEqualTo("Moradia");
                assertThat(categories.getConverter().toString(category)).doesNotContain("Category[");

                Scene scene = new Scene(new javafx.scene.layout.VBox());
                ThemeManager.apply(scene);
                assertThat(scene.getStylesheets()).containsExactly(ThemeManager.stylesheetUrl());
                Dialog<Void> dialog = new Dialog<>();
                ThemeManager.apply(dialog);
                assertThat(dialog.getDialogPane().getStylesheets()).contains(ThemeManager.stylesheetUrl());
                assertThat(dialog.getDialogPane().getStyleClass()).contains("hestia-dialog");
                checked.complete(null);
            } catch (Throwable error) {
                checked.completeExceptionally(error);
            }
        });
        checked.get(10, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @CsvSource({"980,640", "1200,760", "1366,768", "1920,1080"})
    void criticalViewsLayoutAtDesktopResolutions(double width, double height) throws Exception {
        CompletableFuture<Void> checked = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                for (String resource : new String[]{"dashboard-view.fxml", "transactions-view.fxml",
                        "profiles-view.fxml", "categories-view.fxml", "recurring-expenses-view.fxml",
                        "installment-plans-view.fxml", "settings-view.fxml", "reports-view.fxml"}) {
                    Parent root = new FXMLLoader(getClass().getResource("/fxml/" + resource)).load();
                    Scene scene = new Scene(root, width, height);
                    ThemeManager.apply(scene);
                    root.applyCss();
                    root.layout();
                    assertThat(scene.getStylesheets()).as(resource).contains(ThemeManager.stylesheetUrl());
                    assertThat(root.getBoundsInLocal().getWidth()).as(resource).isGreaterThan(0);
                    assertThat(root.lookupAll(".label").stream().filter(Labeled.class::isInstance)
                            .map(Labeled.class::cast).filter(Labeled::isVisible).map(Labeled::getText))
                            .as(resource).doesNotContain("...");
                }
                checked.complete(null);
            } catch (Throwable error) {
                checked.completeExceptionally(error);
            }
        });
        checked.get(20, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @CsvSource({"980,640", "1200,760", "1600,900"})
    void centeredPagesUseTheAvailableViewport(double width, double height) throws Exception {
        CompletableFuture<Void> checked = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                for (String resource : new String[]{"dashboard-view.fxml", "settings-view.fxml",
                        "reports-view.fxml", "transactions-view.fxml", "profiles-view.fxml"}) {
                    Parent shell = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml")).load();
                    Parent view = new FXMLLoader(getClass().getResource("/fxml/" + resource)).load();
                    StackPane contentArea = (StackPane) shell.lookup("#contentArea");
                    contentArea.getChildren().setAll(view);

                    Scene scene = new Scene(shell, width, height);
                    ThemeManager.apply(scene);
                    shell.applyCss();
                    shell.layout();

                    Node pageContent = view.lookup("#pageContent");
                    Node viewport = view instanceof ScrollPane ? view.lookup(".viewport") : null;
                    Bounds viewportBounds = viewport == null
                            ? contentArea.localToScene(contentArea.getBoundsInLocal())
                            : viewport.localToScene(viewport.getBoundsInLocal());
                    Bounds pageBounds = pageContent.localToScene(pageContent.getBoundsInLocal());
                    double leftSpace = pageBounds.getMinX() - viewportBounds.getMinX();
                    double rightSpace = viewportBounds.getMaxX() - pageBounds.getMaxX();

                    assertThat(Math.abs(leftSpace - rightSpace)).as(resource + " centered").isLessThan(1.5);
                    assertThat(pageBounds.getWidth()).as(resource + " bounded width")
                            .isLessThanOrEqualTo(resource.startsWith("settings") ? 820.5 : 1080.5);
                }
                checked.complete(null);
            } catch (Throwable error) {
                checked.completeExceptionally(error);
            }
        });
        checked.get(20, TimeUnit.SECONDS);
    }

    @Test
    void secondaryInterfaceComponentsAreCompactAndConsistent() throws Exception {
        CompletableFuture<Void> checked = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                DatePicker date = DatePickerSupport.configure(new DatePicker(LocalDate.of(2026, 9, 22)));
                assertThat(date.getConverter().toString(date.getValue())).isEqualTo("22/09/2026");
                assertThat(date.getPromptText()).isEqualTo("dd/mm/aaaa");

                VBox longList = new VBox(6);
                for (int index = 1; index <= 25; index++) longList.getChildren().add(new Label("Parcela " + index));
                ScrollPane scroll = DialogSupport.scrollRegion(longList, 280);
                assertThat(scroll.getMaxHeight()).isEqualTo(280);
                assertThat(scroll.isFitToWidth()).isTrue();
                assertThat(scroll.getHbarPolicy()).isEqualTo(ScrollPane.ScrollBarPolicy.NEVER);

                Dialog<Void> dialog = new Dialog<>();
                ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
                ButtonType save = DialogSupport.primaryAction("Salvar");
                dialog.getDialogPane().getButtonTypes().addAll(cancel, save);
                dialog.getDialogPane().setContent(DialogSupport.content("Descrição curta",
                        DialogSupport.section("Informações", null,
                                DialogSupport.field("Nome", new javafx.scene.control.TextField(), true))));
                DialogSupport.prepare(dialog, null, 620, 0);
                assertThat(dialog.getDialogPane().getStyleClass()).contains("hestia-dialog");
                assertThat(dialog.getDialogPane().getPrefWidth()).isEqualTo(620);
                assertThat(dialog.getDialogPane().lookupButton(save).getStyleClass()).contains("primary-button");
                assertThat(dialog.getDialogPane().lookupButton(cancel).getStyleClass()).contains("ghost-button");
                checked.complete(null);
            } catch (Throwable error) {
                checked.completeExceptionally(error);
            }
        });
        checked.get(20, TimeUnit.SECONDS);
    }

    @Test
    void categoriesStayInsideTheirViewportAndUseASecondaryEditor() throws Exception {
        CompletableFuture<Void> checked = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Parent shell = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml")).load();
                Parent categories = new FXMLLoader(getClass().getResource("/fxml/categories-view.fxml")).load();
                StackPane contentArea = (StackPane) shell.lookup("#contentArea");
                contentArea.getChildren().setAll(categories);

                Scene scene = new Scene(shell, 980, 640);
                ThemeManager.apply(scene);
                shell.applyCss();
                shell.layout();

                VBox list = (VBox) categories.lookup("#categoryList");
                list.getChildren().clear();
                for (int index = 1; index <= 60; index++) {
                    Label row = new Label("Categoria de teste " + index);
                    row.setMinHeight(34);
                    list.getChildren().add(row);
                }
                shell.applyCss();
                shell.layout();

                ScrollPane scroll = (ScrollPane) categories.lookup("#categoryScroll");
                VBox workspace = (VBox) categories.lookup("#categoryWorkspace");
                HBox selector = (HBox) categories.lookup("#categoryTypeSelector");
                VBox navigation = (VBox) shell.lookup("#navigation");
                Label preferences = (Label) shell.lookup("#preferencesSection");
                Button categoryButton = navigation.getChildren().stream()
                        .filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> "categories".equals(button.getUserData())).findFirst().orElseThrow();

                assertThat(scroll.getHeight()).isLessThan(list.getBoundsInLocal().getHeight());
                assertThat(scroll.localToScene(scroll.getBoundsInLocal()).getMaxY())
                        .isLessThanOrEqualTo(contentArea.localToScene(contentArea.getBoundsInLocal()).getMaxY());
                assertThat(workspace.getHeight()).isLessThan(categories.getBoundsInLocal().getHeight());
                assertThat(scroll.getStyleClass()).contains("styled-scroll");
                assertThat(categories.lookup("#categoryFormCard")).isNull();
                assertThat(categories.lookupAll(".button").stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).map(Button::getText)).contains("Nova categoria");
                assertThat(selector.getWidth()).isLessThan(280);
                assertThat(preferences.localToScene(preferences.getBoundsInLocal()).getMinY()
                        - categoryButton.localToScene(categoryButton.getBoundsInLocal()).getMaxY()).isLessThan(45);
                assertThat(navigation.getHeight()).isLessThanOrEqualTo(640);

                Button firstNavButton = navigation.getChildren().stream()
                        .filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow();
                assertThat(((Color) firstNavButton.getTextFill()).getBrightness()).isGreaterThan(0.75);
                checked.complete(null);
            } catch (Throwable error) {
                checked.completeExceptionally(error);
            }
        });
        checked.get(20, TimeUnit.SECONDS);
    }
}
