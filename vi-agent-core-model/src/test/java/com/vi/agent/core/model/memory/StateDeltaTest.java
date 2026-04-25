package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.statepatch.PhaseStatePatch;
import com.vi.agent.core.model.memory.statepatch.UserPreferencePatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateDeltaTest {

    @Test
    void builderShouldSupportSingularAppendFields() {
        StateDelta delta = StateDelta.builder()
            .confirmedFactAppend(fact("fact-1"))
            .constraintAppend(constraint("constraint-1"))
            .decisionAppend(decision("decision-1"))
            .openLoopAppend(openLoop("loop-1"))
            .openLoopIdToClose("loop-2")
            .recentToolOutcomeAppend(toolOutcome("tool-1"))
            .sourceCandidateId("candidate-1")
            .build();

        assertEquals(1, delta.getConfirmedFactsAppend().size());
        assertEquals("fact-1", delta.getConfirmedFactsAppend().get(0).getFactId());
        assertEquals(1, delta.getConstraintsAppend().size());
        assertEquals("constraint-1", delta.getConstraintsAppend().get(0).getConstraintId());
        assertEquals(1, delta.getDecisionsAppend().size());
        assertEquals("decision-1", delta.getDecisionsAppend().get(0).getDecisionId());
        assertEquals(1, delta.getOpenLoopsAppend().size());
        assertEquals("loop-1", delta.getOpenLoopsAppend().get(0).getOpenLoopId());
        assertEquals(1, delta.getOpenLoopIdsToClose().size());
        assertEquals("loop-2", delta.getOpenLoopIdsToClose().get(0));
        assertEquals(1, delta.getRecentToolOutcomesAppend().size());
        assertEquals("tool-1", delta.getRecentToolOutcomesAppend().get(0).getDigestId());
        assertEquals(1, delta.getSourceCandidateIds().size());
        assertEquals("candidate-1", delta.getSourceCandidateIds().get(0));
    }

    @Test
    void isEmptyShouldReturnTrueWhenNoBusinessChangeExists() {
        StateDelta delta = StateDelta.builder().build();

        assertTrue(delta.isEmpty());
    }

    @Test
    void sourceCandidateIdsOnlyShouldNotCountAsBusinessChange() {
        StateDelta delta = StateDelta.builder()
            .sessionId("session-1")
            .sourceRunId("run-1")
            .previousStateVersion(1L)
            .targetStateVersion(2L)
            .sourceCandidateId("candidate-1")
            .build();

        assertTrue(delta.isEmpty());
    }

    @Test
    void taskGoalOverrideShouldCountAsBusinessChange() {
        StateDelta delta = StateDelta.builder()
            .taskGoalOverride("new goal")
            .build();

        assertFalse(delta.isEmpty());
    }

    @Test
    void workingModeOverrideShouldCountAsBusinessChange() {
        StateDelta delta = StateDelta.builder()
            .workingModeOverride(WorkingMode.DEBUG_ANALYSIS)
            .build();

        assertFalse(delta.isEmpty());
    }

    @Test
    void appendAndPatchFieldsShouldCountAsBusinessChange() {
        assertFalse(StateDelta.builder().confirmedFactAppend(fact("fact-1")).build().isEmpty());
        assertFalse(StateDelta.builder().constraintAppend(constraint("constraint-1")).build().isEmpty());
        assertFalse(StateDelta.builder()
            .userPreferencesPatch(UserPreferencePatch.builder().answerStyle(AnswerStyle.DIRECT).build())
            .build()
            .isEmpty());
        assertFalse(StateDelta.builder().decisionAppend(decision("decision-1")).build().isEmpty());
        assertFalse(StateDelta.builder().openLoopAppend(openLoop("loop-1")).build().isEmpty());
        assertFalse(StateDelta.builder().openLoopIdToClose("loop-1").build().isEmpty());
        assertFalse(StateDelta.builder().recentToolOutcomeAppend(toolOutcome("tool-1")).build().isEmpty());
        assertFalse(StateDelta.builder()
            .phaseStatePatch(PhaseStatePatch.builder().summaryEnabled(true).build())
            .build()
            .isEmpty());
    }

    @Test
    void toBuilderShouldKeepExistingValuesAndAllowOverride() {
        StateDelta original = StateDelta.builder()
            .taskGoalOverride("old goal")
            .confirmedFactAppend(fact("fact-1"))
            .build();

        StateDelta copied = original.toBuilder()
            .workingModeOverride(WorkingMode.CODE_REVIEW)
            .build();

        assertEquals("old goal", original.getTaskGoalOverride());
        assertEquals(1, copied.getConfirmedFactsAppend().size());
        assertEquals("old goal", copied.getTaskGoalOverride());
        assertEquals(WorkingMode.CODE_REVIEW, copied.getWorkingModeOverride());
    }

    @Test
    void shouldKeepValueFieldsAfterJsonRoundTrip() {
        StateDelta delta = StateDelta.builder()
            .sessionId("session-1")
            .sourceRunId("run-1")
            .previousStateVersion(1L)
            .targetStateVersion(2L)
            .taskGoalOverride("new goal")
            .confirmedFactAppend(fact("fact-1"))
            .workingModeOverride(WorkingMode.TASK_EXECUTION)
            .sourceCandidateId("candidate-1")
            .build();

        StateDelta restored = JsonUtils.jsonToBean(JsonUtils.toJson(delta), StateDelta.class);

        assertEquals("session-1", restored.getSessionId());
        assertEquals("run-1", restored.getSourceRunId());
        assertEquals(1L, restored.getPreviousStateVersion());
        assertEquals(2L, restored.getTargetStateVersion());
        assertEquals("new goal", restored.getTaskGoalOverride());
        assertEquals("fact-1", restored.getConfirmedFactsAppend().get(0).getFactId());
        assertEquals(WorkingMode.TASK_EXECUTION, restored.getWorkingModeOverride());
        assertEquals("candidate-1", restored.getSourceCandidateIds().get(0));
    }

    private ConfirmedFactRecord fact(String id) {
        return ConfirmedFactRecord.builder()
            .factId(id)
            .content("content-" + id)
            .build();
    }

    private ConstraintRecord constraint(String id) {
        return ConstraintRecord.builder()
            .constraintId(id)
            .content("content-" + id)
            .active(true)
            .build();
    }

    private DecisionRecord decision(String id) {
        return DecisionRecord.builder()
            .decisionId(id)
            .decisionText("decision-" + id)
            .build();
    }

    private OpenLoop openLoop(String id) {
        return OpenLoop.builder()
            .openLoopId(id)
            .kind(OpenLoopKind.FOLLOW_UP_ACTION)
            .status(OpenLoopStatus.OPEN)
            .title("title-" + id)
            .build();
    }

    private ToolOutcomeDigest toolOutcome(String id) {
        return ToolOutcomeDigest.builder()
            .digestId(id)
            .digestText("digest-" + id)
            .build();
    }
}
