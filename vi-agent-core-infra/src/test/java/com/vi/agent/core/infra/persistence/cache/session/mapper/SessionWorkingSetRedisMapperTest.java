package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionWorkingSetSnapshotDocument;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionWorkingSetRedisMapperTest {

    private final SessionWorkingSetRedisMapper mapper = new SessionWorkingSetRedisMapper();

    @Test
    void toDocumentShouldKeepWorkingSetSnapshotFields() {
        SessionWorkingSetSnapshot snapshot = SessionWorkingSetSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .workingSetVersion(2L)
            .maxCompletedTurns(3)
            .summaryCoveredToSequenceNo(7L)
            .rawMessageId("msg-1")
            .rawMessageId("msg-2")
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        SessionWorkingSetSnapshotDocument document = mapper.toDocument(snapshot);

        assertEquals("sess-1", document.getSessionId());
        assertEquals("conv-1", document.getConversationId());
        assertEquals(2L, document.getWorkingSetVersion());
        assertEquals(3, document.getMaxCompletedTurns());
        assertEquals(7L, document.getSummaryCoveredToSequenceNo());
        assertEquals("[\"msg-1\",\"msg-2\"]", document.getRawMessageIdsJson());
        assertEquals(1, document.getSnapshotVersion());
        assertEquals(1777075200000L, document.getUpdatedAtEpochMs());
    }

    @Test
    void toModelShouldRestoreWorkingSetSnapshotFields() {
        SessionWorkingSetSnapshotDocument document = SessionWorkingSetSnapshotDocument.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .workingSetVersion(4L)
            .maxCompletedTurns(5)
            .summaryCoveredToSequenceNo(9L)
            .rawMessageIdsJson("[\"msg-a\",\"msg-b\"]")
            .snapshotVersion(1)
            .updatedAtEpochMs(1777075200000L)
            .build();

        SessionWorkingSetSnapshot restored = mapper.toModel(document);

        assertEquals("sess-1", restored.getSessionId());
        assertEquals("conv-1", restored.getConversationId());
        assertEquals(4L, restored.getWorkingSetVersion());
        assertEquals(5, restored.getMaxCompletedTurns());
        assertEquals(9L, restored.getSummaryCoveredToSequenceNo());
        assertEquals(List.of("msg-a", "msg-b"), restored.getRawMessageIds());
        assertEquals(Instant.parse("2026-04-25T00:00:00Z"), restored.getUpdatedAt());
    }
}
