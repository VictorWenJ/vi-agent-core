package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceTargetContractTest {

    @Test
    void shouldUseStableItemLocationInsteadOfDisplayPath() {
        EvidenceTarget target = EvidenceTarget.builder()
            .targetType(EvidenceTargetType.SESSION_STATE_FIELD)
            .targetRef("state-1")
            .targetField("constraints")
            .targetItemId("constraint-1")
            .displayPath("constraints[0]")
            .build();

        assertEquals("constraints", target.getTargetField());
        assertEquals("constraint-1", target.getTargetItemId());
        assertEquals("constraints[0]", target.getDisplayPath());
    }

    @Test
    void shouldSerializeAndDeserializeStableLocationFields() {
        EvidenceTarget target = EvidenceTarget.builder()
            .targetType(EvidenceTargetType.SESSION_STATE_FIELD)
            .targetRef("state-1")
            .targetField("constraints")
            .targetItemId("constraint-1")
            .displayPath("constraints[0]")
            .build();

        EvidenceTarget restored = JsonUtils.jsonToBean(JsonUtils.toJson(target), EvidenceTarget.class);

        assertEquals(EvidenceTargetType.SESSION_STATE_FIELD, restored.getTargetType());
        assertEquals("constraints", restored.getTargetField());
        assertEquals("constraint-1", restored.getTargetItemId());
    }
}
