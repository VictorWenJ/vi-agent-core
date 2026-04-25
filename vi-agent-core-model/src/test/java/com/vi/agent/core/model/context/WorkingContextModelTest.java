package com.vi.agent.core.model.context;

import com.vi.agent.core.model.context.block.ContextBlockSet;
import com.vi.agent.core.model.context.block.RuntimeInstructionBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkingContextModelTest {

    @Test
    void contextBlockSetShouldExposeReadonlyBlocks() {
        RuntimeInstructionBlock block = RuntimeInstructionBlock.builder()
            .blockId("block-1")
            .priority(ContextPriority.MANDATORY)
            .required(true)
            .tokenEstimate(12)
            .decision(ContextAssemblyDecision.KEEP)
            .renderedText("runtime instruction")
            .build();

        ContextBlockSet blockSet = ContextBlockSet.of(List.of(block));

        assertEquals(1, blockSet.size());
        assertEquals(ContextBlockType.RUNTIME_INSTRUCTION, blockSet.getOrderedBlocks().get(0).getBlockType());
        assertThrows(UnsupportedOperationException.class, () -> blockSet.getOrderedBlocks().add(block));
    }

    @Test
    void buildResultShouldKeepContextAndProjectionSeparate() {
        WorkingContext context = WorkingContext.builder()
            .metadata(WorkingContextMetadata.builder()
                .workingContextSnapshotId("wcs-1")
                .agentMode(AgentMode.GENERAL)
                .contextViewType(ContextViewType.MAIN_AGENT)
                .build())
            .blockSet(ContextBlockSet.of(List.of()))
            .build();
        WorkingContextProjection projection = WorkingContextProjection.builder()
            .projectionId("projection-1")
            .workingContextSnapshotId("wcs-1")
            .build();
        ContextPlan contextPlan = ContextPlan.builder()
            .overBudget(false)
            .build();
        ProjectionValidationResult validationResult = ProjectionValidationResult.builder()
            .valid(true)
            .build();

        WorkingContextBuildResult result = WorkingContextBuildResult.builder()
            .context(context)
            .projection(projection)
            .contextPlan(contextPlan)
            .validationResult(validationResult)
            .build();

        assertSame(context, result.getContext());
        assertSame(projection, result.getProjection());
        assertSame(contextPlan, result.getContextPlan());
        assertSame(validationResult, result.getValidationResult());
    }
    @Test
    void workingContextSourceShouldUseArtifactSnapshotIdsInsteadOfEvidenceIds() {
        WorkingContextSource source = WorkingContextSource.builder()
            .transcriptSnapshotVersion(1L)
            .workingSetVersion(2L)
            .stateVersion(3L)
            .summaryVersion(4L)
            .artifactSnapshotId("artifact-snapshot-1")
            .artifactSnapshotId("artifact-snapshot-2")
            .build();

        assertEquals(List.of("artifact-snapshot-1", "artifact-snapshot-2"), source.getArtifactSnapshotIds());
        assertThrows(UnsupportedOperationException.class, () -> source.getArtifactSnapshotIds().add("artifact-snapshot-3"));
    }
}
