package com.vi.agent.core.runtime.context.builder;

import com.vi.agent.core.common.id.WorkingContextSnapshotIdGenerator;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.context.ContextPlan;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextMetadata;
import com.vi.agent.core.model.context.WorkingContextSource;
import com.vi.agent.core.model.context.block.ContextBlockSet;
import com.vi.agent.core.runtime.context.loader.MemoryLoadBundle;
import com.vi.agent.core.runtime.context.loader.WorkingContextLoadCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Builds the canonical WorkingContext from the planner output.
 */
@Component
public class WorkingContextBuilder {

    @Resource
    private WorkingContextSnapshotIdGenerator workingContextSnapshotIdGenerator;

    public WorkingContext build(WorkingContextLoadCommand command, MemoryLoadBundle bundle, ContextPlan contextPlan) {
        return WorkingContext.builder()
            .metadata(WorkingContextMetadata.builder()
                .workingContextSnapshotId(workingContextSnapshotIdGenerator.nextId())
                .conversationId(command.getConversationId())
                .sessionId(command.getSessionId())
                .turnId(command.getTurnId())
                .runId(command.getRunId())
                .contextBuildSeq(1)
                .modelCallSequenceNo(command.getModelCallSequenceNo() == null ? 1 : command.getModelCallSequenceNo())
                .checkpointTrigger(command.getCheckpointTrigger() == null ? CheckpointTrigger.BEFORE_FIRST_MODEL_CALL : command.getCheckpointTrigger())
                .contextViewType(command.getContextViewType() == null ? ContextViewType.MAIN_AGENT : command.getContextViewType())
                .agentMode(command.getAgentMode() == null ? AgentMode.GENERAL : command.getAgentMode())
                .build())
            .source(WorkingContextSource.builder()
                .transcriptSnapshotVersion(null)
                .workingSetVersion(bundle.getWorkingSetVersion())
                .stateVersion(bundle.getLatestState() == null ? null : bundle.getLatestState().getStateVersion())
                .summaryVersion(bundle.getLatestSummary() == null ? null : bundle.getLatestSummary().getSummaryVersion())
                .build())
            .budget(contextPlan.getBudget())
            .blockSet(ContextBlockSet.of(contextPlan.getBlocks()))
            .build();
    }
}
