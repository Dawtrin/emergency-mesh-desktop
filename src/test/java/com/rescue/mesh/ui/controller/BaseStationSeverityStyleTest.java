package com.rescue.mesh.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verifies the protocol severity-to-dashboard triage styling contract. */
class BaseStationSeverityStyleTest {

    @Test
    void mapsKnownSeveritiesToVisibleTriageClasses() {
        assertEquals("severity-critical", BaseStationController.severityStyleClass("CRITICAL"));
        assertEquals("severity-high", BaseStationController.severityStyleClass(" high "));
        assertEquals("severity-medium", BaseStationController.severityStyleClass("medium"));
    }

    @Test
    void doesNotApplySeverityColorToMissingOrUnknownValues() {
        assertNull(BaseStationController.severityStyleClass(null));
        assertNull(BaseStationController.severityStyleClass("LOW"));
    }
}
