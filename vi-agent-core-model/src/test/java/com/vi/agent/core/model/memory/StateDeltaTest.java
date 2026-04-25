package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.statepatch.PhaseStatePatch;
import com.vi.agent.core.model.memory.statepatch.UserPreferencePatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateDeltaTest {

    @Test
    void builderShouldKeepDomainPatchFieldsAndSingularLists() {
        ConfirmedFactRecord fact = ConfirmedFactRecord.builder().factId("fact-1").content("fact").build();
        ConstraintRecord constraint = ConstraintRecord.builder().constraintId("constraint-1").content("constraint").build();
        DecisionRecord decision = DecisionRecord.builder().decisionId("decision-1").decisionText("decision").build();
        OpenLoop openLoop = OpenLoop.builder().openLoopId("loop-1").status(OpenLoopStatus.OPEN).build();
        ToolOutcomeDigest outcome = ToolOutcomeDigest.builder().digestId("tool-1").digestText("done").build();

        StateDelta delta = StateDelta.builder()
            .sessionId("sess-1")
            .sourceRunId("run-1")
            .previousStateVersion(1L)
            .targetStateVersion(2L)
            .taskGoalOverride("更新任务目标")
            .workingModeOverride(WorkingMode.TASK_EXECUTION)
            .confirmedFact(fact)
            .constraint(constraint)
            .decision(decision)
            .openLoop(openLoop)
            .openLoopIdToClose("loop-0")
            .recentToolOutcome(outcome)
            .sourceCandidateId("candidate-1")
            .sourceCandidateId("candidate-2")
            .userPreferencesPatch(UserPreferencePatch.builder().detailLevel(DetailLevel.DETAILED).build())
            .phaseStatePatch(PhaseStatePatch.builder().status("ready").build())
            .build();

        assertEquals("更新任务目标", delta.getTaskGoalOverride());
        assertEquals(WorkingMode.TASK_EXECUTION, delta.getWorkingModeOverride());
        assertEquals(List.of(fact), delta.getConfirmedFactsAppend());
        assertEquals(List.of(constraint), delta.getConstraintsAppend());
        assertEquals(List.of(decision), delta.getDecisionsAppend());
        assertEquals(List.of(openLoop), delta.getOpenLoopsAppend());
        assertEquals(List.of("loop-0"), delta.getOpenLoopIdsToClose());
        assertEquals(List.of(outcome), delta.getRecentToolOutcomesAppend());
        assertEquals(List.of("candidate-1", "candidate-2"), delta.getSourceCandidateIds());
        assertThrows(UnsupportedOperationException.class, () -> delta.getSourceCandidateIds().add("candidate-3"));
    }

    @Test
    void isEmptyShouldIgnoreMetadataAndSourceCandidateIds() {
        StateDelta delta = StateDelta.builder()
            .sessionId("sess-1")
            .sourceRunId("run-1")
            .previousStateVersion(1L)
            .targetStateVersion(2L)
            .sourceCandidateId("candidate-1")
            .build();

        assertTrue(delta.isEmpty());
    }

    @Test
    void shouldSerializeAndDeserializePatchFields() {
        StateDelta delta = StateDelta.builder()
            .sessionId("sess-1")
            .sourceRunId("run-1")
            .previousStateVersion(1L)
            .targetStateVersion(2L)
            .taskGoalOverride("更新任务目标")
            .workingModeOverride(WorkingMode.TASK_EXECUTION)
            .confirmedFact(ConfirmedFactRecord.builder().factId("fact-1").content("fact").build())
            .sourceCandidateId("candidate-1")
            .userPreferencesPatch(UserPreferencePatch.builder().answerStyle(AnswerStyle.STRUCTURED).build())
            .build();

        StateDelta restored = JsonUtils.jsonToBean(JsonUtils.toJson(delta), StateDelta.class);

        assertEquals("更新任务目标", restored.getTaskGoalOverride());
        assertEquals(WorkingMode.TASK_EXECUTION, restored.getWorkingModeOverride());
        assertEquals("fact-1", restored.getConfirmedFactsAppend().get(0).getFactId());
        assertEquals(AnswerStyle.STRUCTURED, restored.getUserPreferencesPatch().getAnswerStyle());
        assertEquals(List.of("candidate-1"), restored.getSourceCandidateIds());
    }
}
