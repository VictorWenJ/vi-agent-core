package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StateMemoryModelContractTest {

    @Test
    void confirmedFactRecordShouldMatchP2V5ClassFragment() {
        assertFields(ConfirmedFactRecord.class, List.of(
            "factId",
            "content",
            "confidence",
            "lastVerifiedAt",
            "stalePolicy"
        ));
        assertFieldType(ConfirmedFactRecord.class, "confidence", Double.class);
        assertFieldType(ConfirmedFactRecord.class, "stalePolicy", StalePolicy.class);
        assertFalse(hasField(ConfirmedFactRecord.class, "category"));
        assertFalse(hasField(ConfirmedFactRecord.class, "evidenceIds"));
        assertFalse(hasField(ConfirmedFactRecord.class, "createdAt"));
        assertFalse(hasField(ConfirmedFactRecord.class, "updatedAt"));
    }

    @Test
    void constraintRecordShouldMatchP2V5ClassFragment() {
        assertFields(ConstraintRecord.class, List.of(
            "constraintId",
            "content",
            "scope",
            "confidence",
            "lastVerifiedAt"
        ));
        assertFieldType(ConstraintRecord.class, "scope", ConstraintScope.class);
        assertFieldType(ConstraintRecord.class, "confidence", Double.class);
        assertFalse(hasField(ConstraintRecord.class, "active"));
        assertFalse(hasField(ConstraintRecord.class, "evidenceIds"));
        assertFalse(hasField(ConstraintRecord.class, "createdAt"));
        assertFalse(hasField(ConstraintRecord.class, "updatedAt"));
    }

    @Test
    void openLoopShouldMatchP2V5ClassFragment() {
        assertFields(OpenLoop.class, List.of(
            "loopId",
            "kind",
            "content",
            "status",
            "sourceType",
            "sourceRef",
            "createdAt",
            "closedAt"
        ));
        assertFieldType(OpenLoop.class, "kind", OpenLoopKind.class);
        assertFieldType(OpenLoop.class, "status", OpenLoopStatus.class);
        assertFalse(hasField(OpenLoop.class, "openLoopId"));
        assertFalse(hasField(OpenLoop.class, "title"));
        assertFalse(hasField(OpenLoop.class, "description"));
        assertFalse(hasField(OpenLoop.class, "evidenceIds"));
        assertFalse(hasField(OpenLoop.class, "updatedAt"));
    }

    @Test
    void toolOutcomeDigestShouldMatchP2V5ClassFragment() {
        assertFields(ToolOutcomeDigest.class, List.of(
            "digestId",
            "toolCallRecordId",
            "toolExecutionId",
            "toolName",
            "summary",
            "freshnessPolicy",
            "validUntil",
            "lastVerifiedAt"
        ));
        assertFieldType(ToolOutcomeDigest.class, "freshnessPolicy", ToolOutcomeFreshnessPolicy.class);
        assertFalse(hasField(ToolOutcomeDigest.class, "digestText"));
        assertFalse(hasField(ToolOutcomeDigest.class, "expiresAt"));
        assertFalse(hasField(ToolOutcomeDigest.class, "evidenceIds"));
    }

    @Test
    void shouldRoundTripV5NestedFieldsThroughJson() {
        StateDelta delta = StateDelta.builder()
            .confirmedFactAppend(ConfirmedFactRecord.builder()
                .factId("fact-1")
                .content("User prefers concise checklists.")
                .confidence(0.9)
                .lastVerifiedAt(Instant.parse("2026-04-26T00:00:00Z"))
                .stalePolicy(StalePolicy.SESSION)
                .build())
            .constraintAppend(ConstraintRecord.builder()
                .constraintId("constraint-1")
                .content("Do not expose internal evidence ids.")
                .scope(ConstraintScope.SESSION)
                .confidence(0.95)
                .lastVerifiedAt(Instant.parse("2026-04-26T00:00:00Z"))
                .build())
            .openLoopAppend(OpenLoop.builder()
                .loopId("loop-1")
                .kind(OpenLoopKind.FOLLOW_UP_ACTION)
                .content("Collect I-20 from user.")
                .status(OpenLoopStatus.OPEN)
                .sourceType("USER")
                .sourceRef("msg-1")
                .createdAt(Instant.parse("2026-04-26T00:00:00Z"))
                .build())
            .recentToolOutcomeAppend(ToolOutcomeDigest.builder()
                .digestId("digest-1")
                .toolCallRecordId("tcr-1")
                .toolExecutionId("tex-1")
                .toolName("searchSchools")
                .summary("Found three candidate schools.")
                .freshnessPolicy(ToolOutcomeFreshnessPolicy.SESSION)
                .validUntil(Instant.parse("2026-04-27T00:00:00Z"))
                .lastVerifiedAt(Instant.parse("2026-04-26T00:00:00Z"))
                .build())
            .build();

        StateDelta restored = JsonUtils.jsonToBean(JsonUtils.toJson(delta), StateDelta.class);

        assertEquals("fact-1", restored.getConfirmedFactsAppend().get(0).getFactId());
        assertEquals(StalePolicy.SESSION, restored.getConfirmedFactsAppend().get(0).getStalePolicy());
        assertEquals(ConstraintScope.SESSION, restored.getConstraintsAppend().get(0).getScope());
        assertEquals("loop-1", restored.getOpenLoopsAppend().get(0).getLoopId());
        assertEquals("Found three candidate schools.", restored.getRecentToolOutcomesAppend().get(0).getSummary());
    }

    private void assertFields(Class<?> type, List<String> expectedFields) {
        List<String> fields = Arrays.stream(type.getDeclaredFields())
            .map(Field::getName)
            .toList();

        assertEquals(expectedFields, fields);
    }

    private void assertFieldType(Class<?> type, String fieldName, Class<?> expectedType) {
        Field field = Arrays.stream(type.getDeclaredFields())
            .filter(candidate -> candidate.getName().equals(fieldName))
            .findFirst()
            .orElseThrow();

        assertEquals(expectedType, field.getType());
    }

    private boolean hasField(Class<?> type, String fieldName) {
        return Arrays.stream(type.getDeclaredFields())
            .anyMatch(field -> field.getName().equals(fieldName));
    }
}
