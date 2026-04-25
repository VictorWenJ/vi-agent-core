package com.vi.agent.core.runtime.context.audit;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.ContextPlan;
import com.vi.agent.core.model.context.ProjectionValidationResult;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextProjection;
import com.vi.agent.core.model.context.WorkingContextSnapshotRecord;
import com.vi.agent.core.model.port.WorkingContextSnapshotRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Persists WorkingContext audit snapshots.
 */
@Component
public class WorkingContextSnapshotService {

    @Resource
    private WorkingContextSnapshotRepository workingContextSnapshotRepository;

    public void save(WorkingContext context, WorkingContextProjection projection, ContextPlan contextPlan, ProjectionValidationResult validationResult) {
        if (context == null || context.getMetadata() == null) {
            return;
        }
        WorkingContextSnapshotRecord snapshotRecord = WorkingContextSnapshotRecord.builder()
            .workingContextSnapshotId(context.getMetadata().getWorkingContextSnapshotId())
            .conversationId(context.getMetadata().getConversationId())
            .sessionId(context.getMetadata().getSessionId())
            .turnId(context.getMetadata().getTurnId())
            .runId(context.getMetadata().getRunId())
            .contextBuildSeq(context.getMetadata().getContextBuildSeq())
            .modelCallSequenceNo(context.getMetadata().getModelCallSequenceNo())
            .checkpointTrigger(context.getMetadata().getCheckpointTrigger())
            .contextViewType(context.getMetadata().getContextViewType())
            .agentMode(context.getMetadata().getAgentMode())
            .transcriptSnapshotVersion(context.getSource() == null ? null : context.getSource().getTranscriptSnapshotVersion())
            .workingSetVersion(context.getSource() == null ? null : context.getSource().getWorkingSetVersion())
            .stateVersion(context.getSource() == null ? null : context.getSource().getStateVersion())
            .summaryVersion(context.getSource() == null ? null : context.getSource().getSummaryVersion())
            .budgetJson(JsonUtils.toJson(context.getBudget()))
            .blockSetJson(JsonUtils.toJson(context.getBlockSet()))
            .contextJson(JsonUtils.toJson(context))
            .projectionJson(JsonUtils.toJson(projection))
            .createdAt(Instant.now())
            .build();
        workingContextSnapshotRepository.save(snapshotRecord);
    }
}
