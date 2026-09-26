package com.rescue.mesh.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxmlControllerContractTest {
    private static final Pattern FX_ID = Pattern.compile("fx:id=\"([^\"]+)\"");
    private static final Pattern ACTION = Pattern.compile("onAction=\"#([^\"]+)\"");

    @Test
    void baseStationFxmlMatchesItsController() throws IOException {
        assertContract("/fxml/BaseStation.fxml", com.rescue.mesh.ui.controller.BaseStationController.class);
    }

    @Test
    void nodeClientFxmlMatchesItsController() throws IOException {
        assertContract("/fxml/NodeClient.fxml", com.rescue.mesh.ui.controller.NodeClientController.class);
    }

    private static void assertContract(String resource, Class<?> controller) throws IOException {
        String fxml;
        try (var stream = FxmlControllerContractTest.class.getResourceAsStream(resource)) {
            assertTrue(stream != null, "Missing FXML resource: " + resource);
            fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> fields = Arrays.stream(controller.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        Matcher ids = FX_ID.matcher(fxml);
        while (ids.find()) {
            assertTrue(fields.contains(ids.group(1)), () -> resource + " has fx:id without controller field: " + ids.group(1));
        }
        Matcher actions = ACTION.matcher(fxml);
        while (actions.find()) {
            String action = actions.group(1);
            assertDoesNotThrow(() -> controller.getDeclaredMethod(action),
                    () -> resource + " has missing controller handler: " + action);
        }
    }
}
