package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhaseStateTest {

    @Test
    void shouldKeepBooleanSwitchFieldsAfterJsonRoundTrip() {
        PhaseState phaseState = PhaseState.builder()
            .promptEngineeringEnabled(true)
            .contextAuditEnabled(true)
            .summaryEnabled(false)
            .stateExtractionEnabled(true)
            .compactionEnabled(false)
            .build();

        PhaseState restored = JsonUtils.jsonToBean(JsonUtils.toJson(phaseState), PhaseState.class);

        assertEquals(Boolean.TRUE, restored.getPromptEngineeringEnabled());
        assertEquals(Boolean.TRUE, restored.getContextAuditEnabled());
        assertEquals(Boolean.FALSE, restored.getSummaryEnabled());
        assertEquals(Boolean.TRUE, restored.getStateExtractionEnabled());
        assertEquals(Boolean.FALSE, restored.getCompactionEnabled());
    }
}
