package com.vi.agent.core.model.context;

import com.vi.agent.core.model.context.block.ContextBlockSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkingContextContractTest {

    @Test
    void workingContextFieldsShouldMatchP2V5ClassSnippet() {
        assertFieldNames(WorkingContext.class, List.of("metadata", "source", "budget", "blockSet"));
    }

    @Test
    void workingContextProjectionFieldsShouldMatchP2V5ClassSnippet() {
        assertFieldNames(WorkingContextProjection.class, List.of(
            "projectionId",
            "workingContextSnapshotId",
            "contextViewType",
            "modelMessages",
            "inputTokenEstimate"
        ));
    }

    @Test
    void contextBudgetSnapshotFieldsShouldMatchP2V5ClassSnippet() {
        assertFieldNames(ContextBudgetSnapshot.class, List.of(
            "modelMaxInputTokens",
            "inputTokenEstimate",
            "reservedOutputTokens",
            "reservedToolLoopTokens",
            "safetyMarginTokens",
            "remainingBudget",
            "overBudget"
        ));
    }

    @Test
    void contextBlockSetFieldsShouldMatchP2V5ClassSnippet() {
        assertFieldNames(ContextBlockSet.class, List.of("orderedBlocks"));
    }

    @Test
    void workingContextMetadataFieldsShouldMatchP2V5ClassSnippet() {
        assertFieldNames(WorkingContextMetadata.class, List.of(
            "workingContextSnapshotId",
            "conversationId",
            "sessionId",
            "turnId",
            "runId",
            "contextBuildSeq",
            "modelCallSequenceNo",
            "checkpointTrigger",
            "contextViewType",
            "agentMode"
        ));
    }

    private void assertFieldNames(Class<?> type, List<String> expectedNames) {
        List<String> actualNames = Arrays.stream(type.getDeclaredFields())
            .map(Field::getName)
            .toList();

        assertEquals(expectedNames, actualNames);
    }
}
