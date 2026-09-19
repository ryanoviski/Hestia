package io.github.ryanoviski.hestia.presentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViewResourcesTest {
    @Test
    void navigationContainsOnlyCurrentModules() throws Exception {
        try (var stream = ViewResourcesTest.class.getResourceAsStream("/fxml/main-view.fxml")) {
            assertThat(stream).isNotNull();
            String fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(fxml)
                    .doesNotContain("userData=\"calendar\"")
                    .doesNotContain("userData=\"documents\"")
                    .doesNotContain("userData=\"budgets\"");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/fxml/main-view.fxml", "/fxml/dashboard-view.fxml",
            "/fxml/profiles-view.fxml", "/fxml/placeholder-view.fxml",
            "/fxml/categories-view.fxml", "/fxml/transactions-view.fxml",
            "/fxml/recurring-expenses-view.fxml", "/fxml/installment-plans-view.fxml",
            "/fxml/calendar-view.fxml", "/db/migrations/V003__create_financial_commitments.sql",
            "/fxml/documents-view.fxml", "/fxml/settings-view.fxml",
            "/db/migrations/V004__create_attachments.sql",
            "/db/migrations/V005__create_category_preferences.sql",
            "/styles/main.css"
    })
    void requiredResourceIsPackaged(String path) throws Exception {
        var resource = ViewResourcesTest.class.getResource(path);
        assertThat(resource).as(path).isNotNull();
        if (path.endsWith(".fxml")) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            try (var stream = resource.openStream()) {
                assertThat(factory.newDocumentBuilder().parse(stream).getDocumentElement()).isNotNull();
            }
        }
    }

    @Test
    void formLabelsAreNotPlaceholderEllipsesAndFiltersHaveReadableWidths() throws Exception {
        List<String> views = List.of("dashboard-view.fxml", "profiles-view.fxml", "categories-view.fxml",
                "transactions-view.fxml", "recurring-expenses-view.fxml", "installment-plans-view.fxml",
                "calendar-view.fxml", "documents-view.fxml", "settings-view.fxml");
        for (String view : views) {
            try (var stream = ViewResourcesTest.class.getResourceAsStream("/fxml/" + view)) {
                assertThat(stream).as(view).isNotNull();
                String fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(fxml).as(view).doesNotContain("text=\"...\"");
            }
        }
        try (var stream = ViewResourcesTest.class.getResourceAsStream("/fxml/transactions-view.fxml")) {
            String fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(fxml).contains("promptText=\"Todas as situações\" minWidth=\"185\"")
                    .contains("promptText=\"Todas as categorias\" minWidth=\"200\"")
                    .contains("promptText=\"Todas as origens\" minWidth=\"175\"");
        }
    }
}
