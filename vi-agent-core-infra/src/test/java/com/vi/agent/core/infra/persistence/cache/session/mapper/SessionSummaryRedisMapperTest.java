package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionSummarySnapshotDocument;
import com.vi.agent.core.model.memory.ConversationSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionSummaryRedisMapperTest {

    private final SessionSummaryRedisMapper mapper = new SessionSummaryRedisMapper();

    @Test
    void shouldRoundTripSummarySnapshotFields() {
        ConversationSummary summary = ConversationSummary.builder()
            .summaryId("sum-1")
            .sessionId("sess-1")
            .summaryVersion(2L)
            .coveredFromSequenceNo(1L)
            .coveredToSequenceNo(10L)
            .summaryText("历史摘要")
            .summaryTemplateKey("summary_extract")
            .summaryTemplateVersion("v1")
            .generatorProvider("deepseek")
            .generatorModel("deepseek-chat")
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        SessionSummarySnapshotDocument document = mapper.toDocument(summary);
        ConversationSummary restored = mapper.toModel(document);

        assertEquals("sum-1", document.getSummaryId());
        assertEquals("sess-1", document.getSessionId());
        assertEquals(2L, document.getSummaryVersion());
        assertEquals(1L, document.getCoveredFromSequenceNo());
        assertEquals(10L, document.getCoveredToSequenceNo());
        assertEquals("历史摘要", document.getSummaryText());
        assertEquals(1, document.getSnapshotVersion());
        assertEquals(1777075200000L, document.getCreatedAtEpochMs());
        assertEquals("sum-1", restored.getSummaryId());
        assertEquals("deepseek-chat", restored.getGeneratorModel());
        assertEquals(Instant.parse("2026-04-25T00:00:00Z"), restored.getCreatedAt());
    }
}
