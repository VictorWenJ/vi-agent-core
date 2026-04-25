package com.vi.agent.core.runtime.memory.evidence;

import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.EvidenceTarget;
import com.vi.agent.core.model.memory.EvidenceTargetType;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import org.springframework.stereotype.Component;

/**
 * Evidence 目标对象工厂。
 */
@Component
public class EvidenceTargetFactory {

    public EvidenceTarget sessionStateField(String stateSnapshotId, String targetField, String targetItemId, String displayPath) {
        return EvidenceTarget.builder()
            .targetType(EvidenceTargetType.SESSION_STATE_FIELD)
            .targetRef(stateSnapshotId)
            .targetField(targetField)
            .targetItemId(targetItemId)
            .displayPath(displayPath)
            .build();
    }

    public EvidenceTarget openLoop(String stateSnapshotId, String loopId, String displayPath) {
        return EvidenceTarget.builder()
            .targetType(EvidenceTargetType.OPEN_LOOP)
            .targetRef(stateSnapshotId)
            .targetField("openLoops")
            .targetItemId(loopId)
            .displayPath(displayPath)
            .build();
    }

    public EvidenceTarget toolOutcome(String stateSnapshotId, ToolOutcomeDigest digest) {
        String digestId = digest == null ? null : digest.getDigestId();
        return EvidenceTarget.builder()
            .targetType(EvidenceTargetType.TOOL_OUTCOME_DIGEST)
            .targetRef(stateSnapshotId)
            .targetField("recentToolOutcomes")
            .targetItemId(digestId)
            .displayPath("recentToolOutcomes[" + digestId + "]")
            .build();
    }

    public EvidenceTarget summaryText(ConversationSummary summary) {
        return EvidenceTarget.builder()
            .targetType(EvidenceTargetType.SUMMARY_SEGMENT)
            .targetRef(summary == null ? null : summary.getSummaryId())
            .targetField("summaryText")
            .targetItemId("summaryText")
            .displayPath("summaryText")
            .build();
    }
}
