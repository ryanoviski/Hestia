package io.github.ryanoviski.hestia.presentation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ViewResourcesTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/fxml/main-view.fxml", "/fxml/dashboard-view.fxml",
            "/fxml/profiles-view.fxml", "/fxml/placeholder-view.fxml",
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
}
