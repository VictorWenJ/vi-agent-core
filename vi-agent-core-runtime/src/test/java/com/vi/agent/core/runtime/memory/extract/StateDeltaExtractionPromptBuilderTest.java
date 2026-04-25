package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateDeltaExtractionPromptBuilderTest {

    @Test
    void promptShouldContainTurnMessagesCurrentStateVersionAndStrictSchemaRules() {
        StateDeltaExtractionPromptBuilder builder = new StateDeltaExtractionPromptBuilder();

        String prompt = builder.buildPrompt(StateDeltaExtractionCommand.builder()
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .agentMode(AgentMode.GENERAL)
            .currentState(SessionStateSnapshot.builder()
                .snapshotId("state-1")
                .sessionId("sess-1")
                .stateVersion(7L)
                .taskGoal("Prepare visa checklist")
                .workingMode(WorkingMode.TASK_EXECUTION)
                .build())
            .turnMessages(List.of(
                UserMessage.create("msg-user-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "I need a concise checklist."),
                AssistantMessage.create("msg-assistant-1", "conv-1", "sess-1", "turn-1", "run-1", 2L, "Use a concise checklist.", List.of(), null, null)
            ))
            .workingContextSnapshotId("wctx-1")
            .build());

        assertTrue(prompt.contains("msg-user-1"));
        assertTrue(prompt.contains("I need a concise checklist."));
        assertTrue(prompt.contains("msg-assistant-1"));
        assertTrue(prompt.contains("currentStateVersion: 7"));
        assertTrue(prompt.contains("Only output StateDelta JSON"));
        assertTrue(prompt.contains("taskGoalOverride"));
        assertTrue(prompt.contains("sourceCandidateIds"));
        assertTrue(prompt.contains("ConfirmedFactRecord fields are only: factId, content, confidence, lastVerifiedAt, stalePolicy."));
        assertTrue(prompt.contains("ConstraintRecord fields are only: constraintId, content, scope, confidence, lastVerifiedAt."));
        assertTrue(prompt.contains("DecisionRecord fields are only: decisionId, content, decidedBy, decidedAt, confidence."));
        assertTrue(prompt.contains("OpenLoop fields are only: loopId, kind, content, status, sourceType, sourceRef, createdAt, closedAt."));
        assertTrue(prompt.contains("ToolOutcomeDigest fields are only: digestId, toolCallRecordId, toolExecutionId, toolName, summary, freshnessPolicy, validUntil, lastVerifiedAt."));
        assertTrue(prompt.contains("UserPreferencePatch fields are only: answerStyle, detailLevel, termFormat."));
        assertTrue(prompt.contains("PhaseStatePatch fields are only: promptEngineeringEnabled, contextAuditEnabled, summaryEnabled, stateExtractionEnabled, compactionEnabled."));
        assertTrue(prompt.contains("Do not output upsert"));
        assertTrue(prompt.contains("Do not output remove"));
        assertFalse(prompt.contains("decisionText"));
        assertFalse(prompt.contains("digestText"));
        assertFalse(prompt.contains("expiresAt"));
        assertFalse(prompt.contains("openLoopId,"));
    }
}
