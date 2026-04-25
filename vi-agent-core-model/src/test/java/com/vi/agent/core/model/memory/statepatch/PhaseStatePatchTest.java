package com.vi.agent.core.model.memory.statepatch;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseStatePatchTest {

    @Test
    void isEmptyShouldReturnTrueWhenNoFieldIsExplicitlyUpdated() {
        PhaseStatePatch patch = PhaseStatePatch.builder().build();

        assertTrue(patch.isEmpty());
    }

    @Test
    void isEmptyShouldReturnFalseWhenAnyFieldIsExplicitlyUpdated() {
        PhaseStatePatch patch = PhaseStatePatch.builder()
            .status("ready")
            .build();

        assertFalse(patch.isEmpty());
    }

    @Test
    void shouldKeepPatchFieldsAfterJsonRoundTrip() {
        Instant updatedAt = Instant.parse("2026-04-25T00:00:00Z");
        PhaseStatePatch patch = PhaseStatePatch.builder()
            .phaseKey("p2-a")
            .phaseName("P2-A")
            .status("ready")
            .updatedAt(updatedAt)
            .build();

        PhaseStatePatch restored = JsonUtils.jsonToBean(JsonUtils.toJson(patch), PhaseStatePatch.class);

        assertEquals("p2-a", restored.getPhaseKey());
        assertEquals("P2-A", restored.getPhaseName());
        assertEquals("ready", restored.getStatus());
        assertEquals(updatedAt, restored.getUpdatedAt());
    }
}
