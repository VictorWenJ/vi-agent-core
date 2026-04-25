package com.vi.agent.core.runtime.context.render;

import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.AnswerStyle;
import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConstraintRecord;
import com.vi.agent.core.model.memory.DecisionRecord;
import com.vi.agent.core.model.memory.DetailLevel;
import com.vi.agent.core.model.memory.OpenLoop;
import com.vi.agent.core.model.memory.OpenLoopStatus;
import com.vi.agent.core.model.memory.PhaseState;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.TermFormat;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.memory.UserPreferenceState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStateBlockRendererTest {

    @Test
    void renderShouldIncludeConcreteStateContents() {
        SessionStateSnapshot state = SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("sess-1")
            .stateVersion(1L)
            .taskGoal("finish visa checklist")
            .workingMode(WorkingMode.DOCUMENT_GOVERNANCE)
            .userPreferences(UserPreferenceState.builder()
                .answerStyle(AnswerStyle.DIRECT)
                .detailLevel(DetailLevel.HIGH)
                .termFormat(TermFormat.ENGLISH_ZH)
                .build())
            .confirmedFact(ConfirmedFactRecord.builder()
                .factId("fact-1")
                .content("User has a valid passport.")
                .build())
            .constraint(ConstraintRecord.builder()
                .constraintId("constraint-1")
                .content("Do not expose internal evidence ids.")
                .build())
            .decision(DecisionRecord.builder()
                .decisionId("decision-1")
                .title("Use concise format")
                .decisionText("Answer with checklist.")
                .build())
            .openLoop(OpenLoop.builder()
                .openLoopId("loop-1")
                .status(OpenLoopStatus.OPEN)
                .title("Collect I-20")
                .description("Need user to upload I-20.")
                .build())
            .recentToolOutcome(ToolOutcomeDigest.builder()
                .digestId("digest-1")
                .toolName("searchSchools")
                .digestText("Found three candidate schools.")
                .build())
            .phaseState(PhaseState.builder()
                .promptEngineeringEnabled(true)
                .contextAuditEnabled(true)
                .summaryEnabled(false)
                .stateExtractionEnabled(false)
                .compactionEnabled(false)
                .build())
            .build();

        String renderedText = new SessionStateBlockRenderer().render(state);

        assertTrue(renderedText.contains("finish visa checklist"));
        assertTrue(renderedText.contains("DOCUMENT_GOVERNANCE"));
        assertTrue(renderedText.contains("DIRECT"));
        assertTrue(renderedText.contains("HIGH"));
        assertTrue(renderedText.contains("ENGLISH_ZH"));
        assertTrue(renderedText.contains("User has a valid passport."));
        assertTrue(renderedText.contains("Do not expose internal evidence ids."));
        assertTrue(renderedText.contains("Use concise format"));
        assertTrue(renderedText.contains("Answer with checklist."));
        assertTrue(renderedText.contains("[OPEN] Collect I-20 - Need user to upload I-20."));
        assertTrue(renderedText.contains("searchSchools - Found three candidate schools."));
        assertTrue(renderedText.contains("promptEngineeringEnabled: true"));
        assertTrue(renderedText.contains("contextAuditEnabled: true"));
        assertTrue(renderedText.contains("summaryEnabled: false"));
        assertTrue(renderedText.contains("stateExtractionEnabled: false"));
        assertTrue(renderedText.contains("compactionEnabled: false"));
    }
}
