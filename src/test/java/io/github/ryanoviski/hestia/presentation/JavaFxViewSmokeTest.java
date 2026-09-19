package io.github.ryanoviski.hestia.presentation;

import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.presentation.components.ColorPalette;
import io.github.ryanoviski.hestia.presentation.components.ComboBoxSupport;
import io.github.ryanoviski.hestia.presentation.components.MonthYearPicker;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
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
            "documents-view.fxml", "settings-view.fxml"})
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
    @CsvSource({"1280,720", "1366,768", "1440,900", "1920,1080"})
    void criticalViewsLayoutAtDesktopResolutions(double width, double height) throws Exception {
        CompletableFuture<Void> checked = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                for (String resource : new String[]{"dashboard-view.fxml", "transactions-view.fxml",
                        "profiles-view.fxml", "categories-view.fxml", "recurring-expenses-view.fxml",
                        "installment-plans-view.fxml", "settings-view.fxml"}) {
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
}
