package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionRecordContractTest {

    @Test
    void shouldMatchP2V5FrozenClassFragment() {
        List<String> fields = Arrays.stream(DecisionRecord.class.getDeclaredFields())
            .map(Field::getName)
            .toList();

        assertEquals(List.of("decisionId", "content", "decidedBy", "decidedAt", "confidence"), fields);
        assertFalse(hasField("title"));
        assertFalse(hasField("decisionText"));
        assertFalse(hasField("rationale"));
        assertFalse(hasField("evidenceIds"));
        assertFalse(hasField("createdAt"));
        assertFalse(hasField("updatedAt"));
    }

    @Test
    void shouldRoundTripFrozenFieldsThroughJson() {
        DecisionRecord record = DecisionRecord.builder()
            .decisionId("decision-1")
            .content("Use concise checklist.")
            .decidedBy("USER")
            .decidedAt(Instant.parse("2026-04-26T00:00:00Z"))
            .confidence(0.9)
            .build();

        DecisionRecord restored = JsonUtils.jsonToBean(JsonUtils.toJson(record), DecisionRecord.class);

        assertEquals("decision-1", restored.getDecisionId());
        assertEquals("Use concise checklist.", restored.getContent());
        assertEquals("USER", restored.getDecidedBy());
        assertEquals(Instant.parse("2026-04-26T00:00:00Z"), restored.getDecidedAt());
        assertEquals(0.9, restored.getConfidence());
    }

    private boolean hasField(String fieldName) {
        return Arrays.stream(DecisionRecord.class.getDeclaredFields())
            .anyMatch(field -> field.getName().equals(fieldName));
    }
}
