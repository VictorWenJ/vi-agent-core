package com.vi.agent.core.runtime.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionMemoryPropertiesTest {

    @Test
    void shouldEnablePostTurnMemoryUpdateByDefault() {
        SessionMemoryProperties properties = new SessionMemoryProperties();

        assertTrue(properties.isPostTurnUpdateEnabled());
        assertTrue(properties.isStateExtractionEnabled());
        assertTrue(properties.isSummaryUpdateEnabled());
    }

    @Test
    void shouldAllowExplicitSwitchesForTestsAndConfigurationBinding() {
        SessionMemoryProperties properties = new SessionMemoryProperties(false, false, false);

        assertFalse(properties.isPostTurnUpdateEnabled());
        assertFalse(properties.isStateExtractionEnabled());
        assertFalse(properties.isSummaryUpdateEnabled());
    }
}
