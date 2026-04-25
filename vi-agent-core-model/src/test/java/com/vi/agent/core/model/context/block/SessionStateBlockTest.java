package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SessionStateBlockTest {

    @Test
    void shouldBindSessionStateSnapshotNotWorkingSetSnapshot() {
        SessionStateSnapshot stateSnapshot = SessionStateSnapshot.builder()
            .snapshotId("snapshot-1")
            .sessionId("sess-1")
            .stateVersion(3L)
            .taskGoal("build project")
            .workingMode(WorkingMode.GENERAL_CONVERSATION)
            .sourceRunId("run-1")
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        SessionStateBlock block = SessionStateBlock.builder()
            .blockId("block-1")
            .priority(ContextPriority.HIGH)
            .required(true)
            .tokenEstimate(128)
            .decision(ContextAssemblyDecision.KEEP)
            .sourceRefs(List.of())
            .evidenceIds(List.of("ev-1"))
            .stateVersion(3L)
            .promptTemplateKey("template")
            .promptTemplateVersion("v1")
            .stateSnapshot(stateSnapshot)
            .renderedText("session state block")
            .build();

        assertEquals(3L, block.getStateVersion());
        assertEquals("sess-1", block.getStateSnapshot().getSessionId());
        assertInstanceOf(SessionStateSnapshot.class, block.getStateSnapshot());
    }
}
