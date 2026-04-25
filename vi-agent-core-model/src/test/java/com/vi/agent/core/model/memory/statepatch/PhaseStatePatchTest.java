package com.vi.agent.core.model.memory.statepatch;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

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
            .contextAuditEnabled(false)
            .build();

        assertFalse(patch.isEmpty());
    }

    @Test
    void shouldKeepPatchFieldsAfterJsonRoundTrip() {
        PhaseStatePatch patch = PhaseStatePatch.builder()
            .promptEngineeringEnabled(true)
            .contextAuditEnabled(false)
            .summaryEnabled(true)
            .stateExtractionEnabled(true)
            .compactionEnabled(false)
            .build();

        PhaseStatePatch restored = JsonUtils.jsonToBean(JsonUtils.toJson(patch), PhaseStatePatch.class);

        assertEquals(Boolean.TRUE, restored.getPromptEngineeringEnabled());
        assertEquals(Boolean.FALSE, restored.getContextAuditEnabled());
        assertEquals(Boolean.TRUE, restored.getSummaryEnabled());
        assertEquals(Boolean.TRUE, restored.getStateExtractionEnabled());
        assertEquals(Boolean.FALSE, restored.getCompactionEnabled());
    }
}
