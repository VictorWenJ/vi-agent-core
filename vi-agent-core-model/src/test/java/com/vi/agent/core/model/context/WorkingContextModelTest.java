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
        assertEquals(ContextBlockType.RUNTIME_INSTRUCTION, blockSet.getBlocks().get(0).getBlockType());
        assertThrows(UnsupportedOperationException.class, () -> blockSet.getBlocks().add(block));
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
}
