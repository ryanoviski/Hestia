package io.github.ryanoviski.hestia.presentation;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
}
