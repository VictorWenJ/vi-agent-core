package com.vi.agent.core.runtime.memory.evidence;

import com.vi.agent.core.model.memory.EvidenceSource;
import com.vi.agent.core.model.memory.EvidenceSourceType;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.SystemMessage;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceSourceFactoryTest {

    private final EvidenceSourceFactory factory = new EvidenceSourceFactory();

    @Test
    void userMessageSourceShouldUseCompletedRawMessageIdentity() {
        EvidenceSource source = factory.fromMessage(
            UserMessage.create("msg-user", "conv-1", "sess-1", "turn-1", "run-1", 1L, "user fact"),
            "wctx-1",
            "task-state"
        ).orElseThrow();

        assertEquals(EvidenceSourceType.USER_MESSAGE, source.getSourceType());
        assertEquals("sess-1", source.getSessionId());
        assertEquals("turn-1", source.getTurnId());
        assertEquals("run-1", source.getRunId());
        assertEquals("msg-user", source.getMessageId());
        assertEquals("wctx-1", source.getWorkingContextSnapshotId());
        assertEquals("task-state", source.getInternalTaskId());
        assertEquals("user fact", source.getExcerptText());
    }

    @Test
    void assistantMessageSourceShouldUseAssistantSourceType() {
        EvidenceSource source = factory.fromMessage(
            AssistantMessage.create("msg-assistant", "conv-1", "sess-1", "turn-1", "run-1", 2L, "assistant fact", java.util.List.of(), null, null),
            "wctx-1",
            "task-state"
        ).orElseThrow();

        assertEquals(EvidenceSourceType.ASSISTANT_MESSAGE, source.getSourceType());
        assertEquals("msg-assistant", source.getMessageId());
    }

    @Test
    void toolMessageSourceShouldUseToolResultSourceTypeAndToolCallRecordId() {
        EvidenceSource source = factory.fromMessage(
            ToolMessage.create("msg-tool", "conv-1", "sess-1", "turn-1", "run-1", 3L, "tool result", "tcr-1", "tc-1", "search", ToolExecutionStatus.SUCCEEDED, null, null, 10L, "{}"),
            "wctx-1",
            "task-state"
        ).orElseThrow();

        assertEquals(EvidenceSourceType.TOOL_RESULT, source.getSourceType());
        assertEquals("msg-tool", source.getMessageId());
        assertEquals("tcr-1", source.getToolCallRecordId());
    }

    @Test
    void toolOutcomeSourceShouldPreferToolCallRecordId() {
        EvidenceSource source = factory.fromToolOutcome(
            ToolOutcomeDigest.builder()
                .toolCallRecordId("tcr-1")
                .toolExecutionId("tex-1")
                .toolName("search")
                .summary("tool digest")
                .build(),
            "sess-1",
            "turn-1",
            "run-1",
            "wctx-1",
            "task-state"
        ).orElseThrow();

        assertEquals(EvidenceSourceType.TOOL_RESULT, source.getSourceType());
        assertEquals("tcr-1", source.getToolCallRecordId());
        assertEquals("tool digest", source.getExcerptText());
    }

    @Test
    void workingContextSnapshotAndInternalTaskSourcesShouldBeSupported() {
        EvidenceSource contextSource = factory.fromWorkingContextSnapshot("sess-1", "turn-1", "run-1", "wctx-1", "context audit");
        EvidenceSource taskSource = factory.fromInternalTask("sess-1", "turn-1", "run-1", "task-summary", "summary task");

        assertEquals(EvidenceSourceType.WORKING_CONTEXT_SNAPSHOT, contextSource.getSourceType());
        assertEquals("wctx-1", contextSource.getWorkingContextSnapshotId());
        assertEquals(EvidenceSourceType.INTERNAL_TASK, taskSource.getSourceType());
        assertEquals("task-summary", taskSource.getInternalTaskId());
    }

    @Test
    void systemSyntheticMessageShouldNotBecomeRawEvidenceSource() {
        Optional<EvidenceSource> source = factory.fromMessage(
            SystemMessage.create("ctxmsg-system", "conv-1", "sess-1", "turn-1", "run-1", -1L, "synthetic context"),
            "wctx-1",
            "task-state"
        );

        assertTrue(source.isEmpty());
    }
}
