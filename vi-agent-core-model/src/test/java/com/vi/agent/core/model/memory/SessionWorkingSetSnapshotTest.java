package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionWorkingSetSnapshotTest {

    @Test
    void builderShouldKeepWorkingSetMetadataAndRawMessageIds() {
        SessionWorkingSetSnapshot snapshot = SessionWorkingSetSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .workingSetVersion(3L)
            .maxCompletedTurns(5)
            .summaryCoveredToSequenceNo(12L)
            .rawMessageId("msg-1")
            .rawMessageId("msg-2")
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        assertEquals("sess-1", snapshot.getSessionId());
        assertEquals(3L, snapshot.getWorkingSetVersion());
        assertEquals(5, snapshot.getMaxCompletedTurns());
        assertEquals(12L, snapshot.getSummaryCoveredToSequenceNo());
        assertEquals(List.of("msg-1", "msg-2"), snapshot.getRawMessageIds());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getRawMessageIds().add("msg-3"));
    }

    @Test
    void shouldSerializeAndDeserializeStableSnapshotFields() {
        SessionWorkingSetSnapshot snapshot = SessionWorkingSetSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .workingSetVersion(1L)
            .maxCompletedTurns(4)
            .summaryCoveredToSequenceNo(9L)
            .rawMessageId("msg-1")
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        SessionWorkingSetSnapshot restored = JsonUtils.jsonToBean(JsonUtils.toJson(snapshot), SessionWorkingSetSnapshot.class);

        assertEquals("sess-1", restored.getSessionId());
        assertEquals(List.of("msg-1"), restored.getRawMessageIds());
        assertEquals(4, restored.getMaxCompletedTurns());
    }
}