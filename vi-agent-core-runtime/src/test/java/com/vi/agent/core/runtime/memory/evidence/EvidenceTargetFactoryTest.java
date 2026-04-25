package com.vi.agent.core.runtime.memory.evidence;

import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.EvidenceTarget;
import com.vi.agent.core.model.memory.EvidenceTargetType;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceTargetFactoryTest {

    private final EvidenceTargetFactory factory = new EvidenceTargetFactory();

    @Test
    void stateFieldTargetShouldUseStableStateItemLocation() {
        EvidenceTarget target = factory.sessionStateField("state-2", "confirmedFacts", "fact-1", "confirmedFacts[fact-1]");

        assertEquals(EvidenceTargetType.SESSION_STATE_FIELD, target.getTargetType());
        assertEquals("state-2", target.getTargetRef());
        assertEquals("confirmedFacts", target.getTargetField());
        assertEquals("fact-1", target.getTargetItemId());
        assertEquals("confirmedFacts[fact-1]", target.getDisplayPath());
    }

    @Test
    void openLoopTargetShouldUseOpenLoopTargetType() {
        EvidenceTarget target = factory.openLoop("state-2", "loop-1", "openLoops[loop-1]");

        assertEquals(EvidenceTargetType.OPEN_LOOP, target.getTargetType());
        assertEquals("state-2", target.getTargetRef());
        assertEquals("openLoops", target.getTargetField());
        assertEquals("loop-1", target.getTargetItemId());
        assertEquals("openLoops[loop-1]", target.getDisplayPath());
    }

    @Test
    void toolOutcomeTargetShouldUseDigestId() {
        EvidenceTarget target = factory.toolOutcome("state-2", ToolOutcomeDigest.builder()
            .digestId("digest-1")
            .toolName("search")
            .summary("tool summary")
            .build());

        assertEquals(EvidenceTargetType.TOOL_OUTCOME_DIGEST, target.getTargetType());
        assertEquals("state-2", target.getTargetRef());
        assertEquals("recentToolOutcomes", target.getTargetField());
        assertEquals("digest-1", target.getTargetItemId());
        assertEquals("recentToolOutcomes[digest-1]", target.getDisplayPath());
    }

    @Test
    void summaryTargetShouldPointToSummaryTextSegment() {
        EvidenceTarget target = factory.summaryText(ConversationSummary.builder()
            .summaryId("summary-2")
            .summaryVersion(2L)
            .summaryText("summary")
            .build());

        assertEquals(EvidenceTargetType.SUMMARY_SEGMENT, target.getTargetType());
        assertEquals("summary-2", target.getTargetRef());
        assertEquals("summaryText", target.getTargetField());
        assertEquals("summaryText", target.getTargetItemId());
        assertEquals("summaryText", target.getDisplayPath());
    }
}
