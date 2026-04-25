package com.vi.agent.core.runtime.memory;

import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.AnswerStyle;
import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConstraintRecord;
import com.vi.agent.core.model.memory.DecisionRecord;
import com.vi.agent.core.model.memory.DetailLevel;
import com.vi.agent.core.model.memory.OpenLoop;
import com.vi.agent.core.model.memory.OpenLoopKind;
import com.vi.agent.core.model.memory.OpenLoopStatus;
import com.vi.agent.core.model.memory.PhaseState;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.memory.TermFormat;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.memory.UserPreferenceState;
import com.vi.agent.core.model.memory.statepatch.PhaseStatePatch;
import com.vi.agent.core.model.memory.statepatch.UserPreferencePatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StateDeltaMergerTest {

    private final StateDeltaMerger merger = new StateDeltaMerger();

    @Test
    void shouldOverrideTaskGoalAndWorkingMode() {
        SessionStateSnapshot merged = merger.merge(baseState(), StateDelta.builder()
            .taskGoalOverride("new goal")
            .workingModeOverride(WorkingMode.DEBUG_ANALYSIS)
            .build());

        assertEquals("new goal", merged.getTaskGoal());
        assertEquals(WorkingMode.DEBUG_ANALYSIS, merged.getWorkingMode());
    }

    @Test
    void shouldAppendConfirmedFactsAndConstraintsWithoutDuplicateIds() {
        SessionStateSnapshot merged = merger.merge(baseState(), StateDelta.builder()
            .confirmedFactAppend(fact("fact-1", "duplicate"))
            .confirmedFactAppend(fact("fact-2", "new"))
            .constraintAppend(constraint("constraint-1", "duplicate"))
            .constraintAppend(constraint("constraint-2", "new"))
            .build());

        assertEquals(2, merged.getConfirmedFacts().size());
        assertEquals("fact-1", merged.getConfirmedFacts().get(0).getFactId());
        assertEquals("old fact", merged.getConfirmedFacts().get(0).getContent());
        assertEquals("fact-2", merged.getConfirmedFacts().get(1).getFactId());
        assertEquals(2, merged.getConstraints().size());
        assertEquals("constraint-1", merged.getConstraints().get(0).getConstraintId());
        assertEquals("old constraint", merged.getConstraints().get(0).getContent());
        assertEquals("constraint-2", merged.getConstraints().get(1).getConstraintId());
    }

    @Test
    void shouldPatchUserPreferencesWithNonNullFieldsOnly() {
        SessionStateSnapshot merged = merger.merge(baseState(), StateDelta.builder()
            .userPreferencesPatch(UserPreferencePatch.builder()
                .detailLevel(DetailLevel.HIGH)
                .build())
            .build());

        assertEquals(AnswerStyle.DIRECT, merged.getUserPreferences().getAnswerStyle());
        assertEquals(DetailLevel.HIGH, merged.getUserPreferences().getDetailLevel());
        assertEquals(TermFormat.ENGLISH_ONLY, merged.getUserPreferences().getTermFormat());
    }

    @Test
    void shouldAppendDecisionsWithoutDuplicateIds() {
        SessionStateSnapshot merged = merger.merge(baseState(), StateDelta.builder()
            .decisionAppend(decision("decision-1", "duplicate"))
            .decisionAppend(decision("decision-2", "new"))
            .build());

        assertEquals(2, merged.getDecisions().size());
        assertEquals("decision-1", merged.getDecisions().get(0).getDecisionId());
        assertEquals("old decision", merged.getDecisions().get(0).getDecisionText());
        assertEquals("decision-2", merged.getDecisions().get(1).getDecisionId());
    }

    @Test
    void shouldAppendOpenLoopsAndCloseOpenLoopsByLoopId() {
        SessionStateSnapshot merged = merger.merge(baseState(), StateDelta.builder()
            .openLoopIdToClose("loop-1")
            .openLoopAppend(openLoop("loop-2", OpenLoopStatus.OPEN))
            .build());

        assertEquals(2, merged.getOpenLoops().size());
        assertEquals(OpenLoopStatus.CLOSED, merged.getOpenLoops().get(0).getStatus());
        assertEquals("loop-2", merged.getOpenLoops().get(1).getOpenLoopId());
    }

    @Test
    void shouldKeepOnlyMostRecentToolOutcomes() {
        StateDelta.StateDeltaBuilder builder = StateDelta.builder();
        for (int i = 0; i < StateDeltaMerger.MAX_RECENT_TOOL_OUTCOMES + 2; i++) {
            builder.recentToolOutcomeAppend(toolOutcome("new-" + i));
        }

        SessionStateSnapshot merged = merger.merge(baseState(), builder.build());

        assertEquals(StateDeltaMerger.MAX_RECENT_TOOL_OUTCOMES, merged.getRecentToolOutcomes().size());
        assertEquals("new-2", merged.getRecentToolOutcomes().get(0).getDigestId());
    }

    @Test
    void shouldPatchPhaseStateWithNonNullFieldsOnly() {
        SessionStateSnapshot merged = merger.merge(baseState(), StateDelta.builder()
            .phaseStatePatch(PhaseStatePatch.builder()
                .summaryEnabled(true)
                .compactionEnabled(true)
                .build())
            .build());

        assertEquals(Boolean.TRUE, merged.getPhaseState().getPromptEngineeringEnabled());
        assertEquals(Boolean.TRUE, merged.getPhaseState().getContextAuditEnabled());
        assertEquals(Boolean.TRUE, merged.getPhaseState().getSummaryEnabled());
        assertEquals(Boolean.TRUE, merged.getPhaseState().getStateExtractionEnabled());
        assertEquals(Boolean.TRUE, merged.getPhaseState().getCompactionEnabled());
    }

    @Test
    void sourceCandidateIdsShouldNotChangeFinalStateBusinessFields() {
        SessionStateSnapshot merged = merger.merge(baseState(), StateDelta.builder()
            .sourceCandidateId("candidate-1")
            .build());

        assertEquals("old goal", merged.getTaskGoal());
        assertEquals(WorkingMode.TASK_EXECUTION, merged.getWorkingMode());
        assertEquals(1, merged.getConfirmedFacts().size());
        assertEquals(1, merged.getConstraints().size());
        assertEquals(1, merged.getDecisions().size());
        assertEquals(1, merged.getOpenLoops().size());
        assertEquals(1, merged.getRecentToolOutcomes().size());
        assertEquals(Boolean.TRUE, merged.getPhaseState().getPromptEngineeringEnabled());
    }

    @Test
    void shouldCreatePreferenceAndPhaseStateWhenBaseFieldsAreMissing() {
        SessionStateSnapshot base = SessionStateSnapshot.builder()
            .sessionId("sess-1")
            .stateVersion(1L)
            .build();

        SessionStateSnapshot merged = merger.merge(base, StateDelta.builder()
            .userPreferencesPatch(UserPreferencePatch.builder().answerStyle(AnswerStyle.EXPLANATORY).build())
            .phaseStatePatch(PhaseStatePatch.builder().stateExtractionEnabled(true).build())
            .build());

        assertEquals(AnswerStyle.EXPLANATORY, merged.getUserPreferences().getAnswerStyle());
        assertNull(merged.getUserPreferences().getDetailLevel());
        assertEquals(Boolean.TRUE, merged.getPhaseState().getStateExtractionEnabled());
        assertNull(merged.getPhaseState().getSummaryEnabled());
    }

    private SessionStateSnapshot baseState() {
        return SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("sess-1")
            .stateVersion(1L)
            .taskGoal("old goal")
            .workingMode(WorkingMode.TASK_EXECUTION)
            .confirmedFact(fact("fact-1", "old fact"))
            .constraint(constraint("constraint-1", "old constraint"))
            .decision(decision("decision-1", "old decision"))
            .userPreferences(UserPreferenceState.builder()
                .answerStyle(AnswerStyle.DIRECT)
                .detailLevel(DetailLevel.LOW)
                .termFormat(TermFormat.ENGLISH_ONLY)
                .build())
            .openLoop(openLoop("loop-1", OpenLoopStatus.OPEN))
            .recentToolOutcome(toolOutcome("old-tool"))
            .phaseState(PhaseState.builder()
                .promptEngineeringEnabled(true)
                .contextAuditEnabled(true)
                .summaryEnabled(false)
                .stateExtractionEnabled(true)
                .compactionEnabled(false)
                .build())
            .build();
    }

    private ConfirmedFactRecord fact(String id, String content) {
        return ConfirmedFactRecord.builder().factId(id).content(content).build();
    }

    private ConstraintRecord constraint(String id, String content) {
        return ConstraintRecord.builder().constraintId(id).content(content).active(true).build();
    }

    private DecisionRecord decision(String id, String text) {
        return DecisionRecord.builder().decisionId(id).decisionText(text).build();
    }

    private OpenLoop openLoop(String id, OpenLoopStatus status) {
        return OpenLoop.builder()
            .openLoopId(id)
            .kind(OpenLoopKind.FOLLOW_UP_ACTION)
            .status(status)
            .title("title-" + id)
            .description("description-" + id)
            .evidenceIds(List.of("evidence-" + id))
            .build();
    }

    private ToolOutcomeDigest toolOutcome(String id) {
        return ToolOutcomeDigest.builder().digestId(id).digestText("digest-" + id).build();
    }
}
