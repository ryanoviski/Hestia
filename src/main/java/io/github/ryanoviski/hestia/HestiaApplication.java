package io.github.ryanoviski.hestia;

import io.github.ryanoviski.hestia.config.ApplicationBootstrap;
import io.github.ryanoviski.hestia.config.ApplicationContext;
import io.github.ryanoviski.hestia.presentation.controllers.MainController;
import io.github.ryanoviski.hestia.presentation.components.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HestiaApplication extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(HestiaApplication.class);
    private ApplicationContext context;

    public static void launchApplication(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            context = ApplicationBootstrap.initialize();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
            Parent root = loader.load();
            MainController controller = loader.getController();
            controller.configure(context);

            Scene scene = new Scene(root, 1200, 760);
            ThemeManager.apply(scene);
            stage.setTitle("Hestia — Gestão financeira familiar");
            stage.getIcons().add(loadApplicationIcon());
            stage.setMinWidth(980);
            stage.setMinHeight(640);
            stage.setScene(scene);
            stage.show();
        } catch (Exception exception) {
            LOGGER.error("Critical error while starting Hestia", exception);
            showCriticalError();
        }
    }

    private Image loadApplicationIcon() throws java.io.IOException {
        var resource = getClass().getResource("/images/branding/hestia-symbol.png");
        if (resource == null) {
            throw new java.io.IOException("Required application icon not found");
        }
        return new Image(resource.toExternalForm());
    }

    private void showCriticalError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Não foi possível iniciar o Hestia");
        alert.setHeaderText("O Hestia encontrou um problema ao iniciar.");
        alert.setContentText("Verifique o arquivo de log para obter detalhes e tente novamente.");
        ThemeManager.apply(alert);
        alert.showAndWait();
        Platform.exit();
    }
}
