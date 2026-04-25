package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceRefContractTest {

    @Test
    void shouldBindTargetAndSourceWithoutDuplicatingLocationFields() {
        EvidenceRef evidenceRef = EvidenceRef.builder()
            .evidenceId("ev-1")
            .target(EvidenceTarget.builder()
                .targetType(EvidenceTargetType.SESSION_STATE)
                .targetRef("state-1")
                .targetField("constraints")
                .targetItemId("constraint-1")
                .displayPath("constraints[0]")
                .build())
            .source(EvidenceSource.builder()
                .sourceType(EvidenceSourceType.USER_MESSAGE)
                .sessionId("sess-1")
                .turnId("turn-1")
                .runId("run-1")
                .messageId("msg-1")
                .excerptText("用户明确要求")
                .build())
            .confidence(new BigDecimal("0.99"))
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        assertEquals("ev-1", evidenceRef.getEvidenceId());
        assertEquals("constraint-1", evidenceRef.getTarget().getTargetItemId());
        assertEquals("msg-1", evidenceRef.getSource().getMessageId());
    }

    @Test
    void shouldSerializeAndDeserializeEvidenceRef() {
        EvidenceRef evidenceRef = EvidenceRef.builder()
            .evidenceId("ev-1")
            .target(EvidenceTarget.builder()
                .targetType(EvidenceTargetType.SESSION_STATE)
                .targetRef("state-1")
                .targetField("constraints")
                .targetItemId("constraint-1")
                .displayPath("constraints[0]")
                .build())
            .source(EvidenceSource.builder()
                .sourceType(EvidenceSourceType.USER_MESSAGE)
                .sessionId("sess-1")
                .turnId("turn-1")
                .runId("run-1")
                .messageId("msg-1")
                .excerptText("用户明确要求")
                .build())
            .confidence(new BigDecimal("0.99"))
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        EvidenceRef restored = JsonUtils.jsonToBean(JsonUtils.toJson(evidenceRef), EvidenceRef.class);

        assertEquals("ev-1", restored.getEvidenceId());
        assertEquals("constraints", restored.getTarget().getTargetField());
        assertEquals("用户明确要求", restored.getSource().getExcerptText());
    }
}