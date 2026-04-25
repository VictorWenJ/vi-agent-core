package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.WorkingMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionStateSnapshotTest {

    @Test
    void builderShouldKeepTypedStateSchemaAndWorkingMode() {
        SessionStateSnapshot snapshot = SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("sess-1")
            .stateVersion(7L)
            .taskGoal("完成 P2-A 验收")
            .workingMode(WorkingMode.ARCHITECTURE_DESIGN)
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        assertEquals("state-1", snapshot.getSnapshotId());
        assertEquals("完成 P2-A 验收", snapshot.getTaskGoal());
        assertEquals(WorkingMode.ARCHITECTURE_DESIGN, snapshot.getWorkingMode());
    }

    @Test
    void shouldSerializeAndDeserializeTypedStateSchema() {
        SessionStateSnapshot snapshot = SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("sess-1")
            .stateVersion(7L)
            .taskGoal("完成 P2-A 验收")
            .workingMode(WorkingMode.ARCHITECTURE_DESIGN)
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        SessionStateSnapshot restored = JsonUtils.jsonToBean(JsonUtils.toJson(snapshot), SessionStateSnapshot.class);

        assertEquals("state-1", restored.getSnapshotId());
        assertEquals(7L, restored.getStateVersion());
        assertEquals(WorkingMode.ARCHITECTURE_DESIGN, restored.getWorkingMode());
    }
}
