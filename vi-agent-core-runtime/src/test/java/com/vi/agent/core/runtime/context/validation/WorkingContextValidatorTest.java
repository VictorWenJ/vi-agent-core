package com.vi.agent.core.runtime.context.validation;

import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import com.vi.agent.core.model.context.ProjectionValidationResult;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextProjection;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import com.vi.agent.core.runtime.context.ContextTestFixtures;
import com.vi.agent.core.runtime.context.projector.WorkingContextProjector;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkingContextValidatorTest {

    private final WorkingContextValidator validator = new WorkingContextValidator();

    @Test
    void validProjectionShouldPass() {
        WorkingContext context = validContext(ContextTestFixtures.budget(50));
        WorkingContextProjection projection = new WorkingContextProjector().project(context);

        ProjectionValidationResult result = validator.validate(context, projection);

        assertTrue(result.isValid());
    }

    @Test
    void missingRequiredBlockShouldFail() {
        WorkingContext context = ContextTestFixtures.context(List.of(
            ContextTestFixtures.recentMessagesBlock(List.of()),
            ContextTestFixtures.currentUserBlock()
        ), ContextTestFixtures.budget(50));
        WorkingContextProjection projection = WorkingContextProjection.builder()
            .projectionId("wcp-1")
            .workingContextSnapshotId("wctx-1")
            .contextViewType(context.getMetadata().getContextViewType())
            .modelMessages(List.of(ContextTestFixtures.currentUserMessage()))
            .inputTokenEstimate(10)
            .build();

        ProjectionValidationResult result = validator.validate(context, projection);

        assertFalse(result.isValid());
    }

    @Test
    void currentUserMessageNotLastShouldFail() {
        WorkingContext context = validContext(ContextTestFixtures.budget(50));
        WorkingContextProjection projection = WorkingContextProjection.builder()
            .projectionId("wcp-1")
            .workingContextSnapshotId("wctx-1")
            .contextViewType(context.getMetadata().getContextViewType())
            .modelMessages(List.of(ContextTestFixtures.currentUserMessage(), ContextTestFixtures.runtimeSystemMessage()))
            .inputTokenEstimate(10)
            .build();

        ProjectionValidationResult result = validator.validate(context, projection);

        assertFalse(result.isValid());
    }

    @Test
    void illegalToolChainShouldFail() {
        AssistantToolCall toolCall = AssistantToolCall.builder()
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .assistantMessageId("msg-assistant")
            .conversationId(ContextTestFixtures.CONVERSATION_ID)
            .sessionId(ContextTestFixtures.SESSION_ID)
            .turnId(ContextTestFixtures.TURN_ID)
            .runId(ContextTestFixtures.RUN_ID)
            .toolName("tool-a")
            .argumentsJson("{}")
            .callIndex(0)
            .status(ToolCallStatus.CREATED)
            .createdAt(Instant.now())
            .build();
        AssistantMessage assistantMessage = AssistantMessage.create(
            "msg-assistant",
            ContextTestFixtures.CONVERSATION_ID,
            ContextTestFixtures.SESSION_ID,
            ContextTestFixtures.TURN_ID,
            ContextTestFixtures.RUN_ID,
            2L,
            "call tool",
            List.of(toolCall),
            null,
            null
        );
        WorkingContext context = ContextTestFixtures.context(List.of(
            ContextTestFixtures.runtimeBlock(),
            ContextTestFixtures.recentMessagesBlock(List.of(assistantMessage)),
            ContextTestFixtures.currentUserBlock()
        ), ContextTestFixtures.budget(50));
        WorkingContextProjection projection = new WorkingContextProjector().project(context);

        ProjectionValidationResult result = validator.validate(context, projection);

        assertFalse(result.isValid());
    }

    @Test
    void legalToolChainShouldPass() {
        AssistantToolCall toolCall = AssistantToolCall.builder()
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .assistantMessageId("msg-assistant")
            .conversationId(ContextTestFixtures.CONVERSATION_ID)
            .sessionId(ContextTestFixtures.SESSION_ID)
            .turnId(ContextTestFixtures.TURN_ID)
            .runId(ContextTestFixtures.RUN_ID)
            .toolName("tool-a")
            .argumentsJson("{}")
            .callIndex(0)
            .status(ToolCallStatus.CREATED)
            .createdAt(Instant.now())
            .build();
        AssistantMessage assistantMessage = AssistantMessage.create(
            "msg-assistant",
            ContextTestFixtures.CONVERSATION_ID,
            ContextTestFixtures.SESSION_ID,
            ContextTestFixtures.TURN_ID,
            ContextTestFixtures.RUN_ID,
            2L,
            "call tool",
            List.of(toolCall),
            null,
            null
        );
        ToolMessage toolMessage = ToolMessage.create(
            "msg-tool",
            ContextTestFixtures.CONVERSATION_ID,
            ContextTestFixtures.SESSION_ID,
            ContextTestFixtures.TURN_ID,
            ContextTestFixtures.RUN_ID,
            3L,
            "tool result",
            "tcr-1",
            "call-1",
            "tool-a",
            ToolExecutionStatus.SUCCEEDED,
            null,
            null,
            1L,
            "{}"
        );
        WorkingContext context = ContextTestFixtures.context(List.of(
            ContextTestFixtures.runtimeBlock(),
            ContextTestFixtures.recentMessagesBlock(List.of(assistantMessage, toolMessage)),
            ContextTestFixtures.currentUserBlock()
        ), ContextTestFixtures.budget(50));
        WorkingContextProjection projection = new WorkingContextProjector().project(context);

        ProjectionValidationResult result = validator.validate(context, projection);

        assertTrue(result.isValid());
    }

    @Test
    void overBudgetShouldFail() {
        ContextBudgetSnapshot budget = ContextBudgetSnapshot.builder()
            .modelMaxInputTokens(100)
            .inputTokenEstimate(80)
            .reservedOutputTokens(20)
            .reservedToolLoopTokens(20)
            .safetyMarginTokens(10)
            .remainingBudget(-30)
            .overBudget(true)
            .build();
        WorkingContext context = validContext(budget);
        WorkingContextProjection projection = new WorkingContextProjector().project(context);

        ProjectionValidationResult result = validator.validate(context, projection);

        assertFalse(result.isValid());
    }

    private WorkingContext validContext(ContextBudgetSnapshot budget) {
        return ContextTestFixtures.context(List.of(
            ContextTestFixtures.runtimeBlock(),
            ContextTestFixtures.recentMessagesBlock(List.of(ContextTestFixtures.recentUserMessage())),
            ContextTestFixtures.currentUserBlock()
        ), budget);
    }
}
