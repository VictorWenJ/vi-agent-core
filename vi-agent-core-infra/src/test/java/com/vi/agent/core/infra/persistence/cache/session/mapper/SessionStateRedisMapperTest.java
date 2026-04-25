package com.vi.agent.core.infra.persistence.cache.session.mapper;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateSnapshotDocument;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.AnswerStyle;
import com.vi.agent.core.model.memory.DetailLevel;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.TermFormat;
import com.vi.agent.core.model.memory.UserPreferenceState;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionStateRedisMapperTest {

    private final SessionStateRedisMapper mapper = new SessionStateRedisMapper();

    @Test
    void shouldRoundTripCompleteStateSnapshotJson() {
        SessionStateSnapshot snapshot = SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("sess-1")
            .stateVersion(3L)
            .taskGoal("finish P2-B")
            .userPreferences(UserPreferenceState.builder()
                .answerStyle(AnswerStyle.DIRECT)
                .detailLevel(DetailLevel.HIGH)
                .termFormat(TermFormat.ENGLISH_ZH)
                .build())
            .workingMode(WorkingMode.TASK_EXECUTION)
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        SessionStateSnapshotDocument document = mapper.toDocument(snapshot);
        SessionStateSnapshot restored = mapper.toModel(document);

        assertEquals("state-1", document.getSnapshotId());
        assertEquals("sess-1", document.getSessionId());
        assertEquals(3L, document.getStateVersion());
        assertEquals("finish P2-B", document.getTaskGoal());
        assertEquals(1, document.getSnapshotVersion());
        assertEquals(1777075200000L, document.getUpdatedAtEpochMs());
        assertEquals("state-1", restored.getSnapshotId());
        assertEquals(WorkingMode.TASK_EXECUTION, restored.getWorkingMode());
        assertEquals(AnswerStyle.DIRECT, restored.getUserPreferences().getAnswerStyle());
    }
}
