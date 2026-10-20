package com.devin.ontology.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthOptionsTest {
    @Test
    void exposesPasswordAndConfiguredWorkbenchAvailabilityWithoutAnEnvelope() {
        AuthController controller = new AuthController(null, null, true);

        assertEquals(Map.of("passwordEnabled", true, "workbenchEnabled", true), controller.options());
    }

    @Test
    void workbenchAvailabilityDefaultsToFalseWhenPropertyIsUnset() {
        AuthController controller = new AuthController(null, null, false);

        assertEquals(Map.of("passwordEnabled", true, "workbenchEnabled", false), controller.options());
    }
}
