package com.vi.agent.core.runtime.context.render;

import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.AnswerStyle;
import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConstraintScope;
import com.vi.agent.core.model.memory.ConstraintRecord;
import com.vi.agent.core.model.memory.DecisionRecord;
import com.vi.agent.core.model.memory.DetailLevel;
import com.vi.agent.core.model.memory.OpenLoop;
import com.vi.agent.core.model.memory.OpenLoopKind;
import com.vi.agent.core.model.memory.OpenLoopStatus;
import com.vi.agent.core.model.memory.PhaseState;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StalePolicy;
import com.vi.agent.core.model.memory.TermFormat;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.memory.ToolOutcomeFreshnessPolicy;
import com.vi.agent.core.model.memory.UserPreferenceState;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
                .confidence(0.98)
                .stalePolicy(StalePolicy.SESSION)
                .build())
            .constraint(ConstraintRecord.builder()
                .constraintId("constraint-1")
                .content("Do not expose internal evidence ids.")
                .scope(ConstraintScope.SESSION)
                .confidence(0.99)
                .build())
            .decision(DecisionRecord.builder()
                .decisionId("decision-1")
                .content("Answer with checklist.")
                .decidedBy("USER")
                .decidedAt(Instant.parse("2026-04-26T00:00:00Z"))
                .confidence(0.9)
                .build())
            .openLoop(OpenLoop.builder()
                .loopId("loop-1")
                .kind(OpenLoopKind.FOLLOW_UP_ACTION)
                .status(OpenLoopStatus.OPEN)
                .content("Need user to upload I-20.")
                .sourceType("USER")
                .sourceRef("msg-user-1")
                .createdAt(Instant.parse("2026-04-26T00:00:00Z"))
                .build())
            .recentToolOutcome(ToolOutcomeDigest.builder()
                .digestId("digest-1")
                .toolCallRecordId("tcr-1")
                .toolExecutionId("tex-1")
                .toolName("searchSchools")
                .summary("Found three candidate schools.")
                .freshnessPolicy(ToolOutcomeFreshnessPolicy.SESSION)
                .validUntil(Instant.parse("2026-04-27T00:00:00Z"))
                .lastVerifiedAt(Instant.parse("2026-04-26T00:00:00Z"))
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
        assertTrue(renderedText.contains("Answer with checklist."));
        assertTrue(renderedText.contains("USER"));
        assertTrue(renderedText.contains("2026-04-26T00:00:00Z"));
        assertTrue(renderedText.contains("[OPEN] FOLLOW_UP_ACTION - Need user to upload I-20."));
        assertTrue(renderedText.contains("msg-user-1"));
        assertTrue(renderedText.contains("searchSchools - Found three candidate schools."));
        assertTrue(renderedText.contains("SESSION"));
        assertTrue(renderedText.contains("2026-04-27T00:00:00Z"));
        assertTrue(renderedText.contains("promptEngineeringEnabled: true"));
        assertTrue(renderedText.contains("contextAuditEnabled: true"));
        assertTrue(renderedText.contains("summaryEnabled: false"));
        assertTrue(renderedText.contains("stateExtractionEnabled: false"));
        assertTrue(renderedText.contains("compactionEnabled: false"));
    }
}
