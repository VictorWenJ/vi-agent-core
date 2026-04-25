package com.vi.agent.core.runtime.context.validation;

import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import com.vi.agent.core.model.context.ProjectionValidationResult;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextProjection;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.model.context.block.CurrentUserMessageBlock;
import com.vi.agent.core.model.context.block.RecentMessagesBlock;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.SummaryMessage;
import com.vi.agent.core.model.message.SystemMessage;
import com.vi.agent.core.model.message.ToolMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * Validates the provider-ready WorkingContext projection before model calls.
 */
@Component
public class WorkingContextValidator {

    public ProjectionValidationResult validate(WorkingContext context, WorkingContextProjection projection) {
        List<String> violations = new ArrayList<>();
        if (context == null) {
            violations.add("working context is null");
        }
        if (projection == null) {
            violations.add("working context projection is null");
        }
        if (context == null || projection == null) {
            return result(violations);
        }

        List<Message> modelMessages = projection.getModelMessages() == null ? List.of() : projection.getModelMessages();
        if (modelMessages.isEmpty()) {
            violations.add("projection modelMessages is empty");
        }

        List<ContextBlock> blocks = context.getBlockSet() == null ? List.of() : context.getBlockSet().getOrderedBlocks();
        validateRequiredBlocks(blocks, violations);
        validateCurrentUserMessageLast(blocks, modelMessages, violations);
        validateDerivedMessagesNotInRecentRawMessages(blocks, violations);
        validateToolChain(modelMessages, violations);
        validateBudget(context.getBudget(), projection, violations);
        return result(violations);
    }

    private void validateRequiredBlocks(List<ContextBlock> blocks, List<String> violations) {
        boolean hasRuntimeInstruction = blocks.stream()
            .anyMatch(block -> block.getBlockType() == ContextBlockType.RUNTIME_INSTRUCTION && block.isRequired());
        boolean hasCurrentUserMessage = blocks.stream()
            .anyMatch(block -> block.getBlockType() == ContextBlockType.CURRENT_USER_MESSAGE && block.isRequired());
        if (!hasRuntimeInstruction) {
            violations.add("required RuntimeInstructionBlock is missing");
        }
        if (!hasCurrentUserMessage) {
            violations.add("required CurrentUserMessageBlock is missing");
        }
    }

    private void validateCurrentUserMessageLast(
        List<ContextBlock> blocks,
        List<Message> modelMessages,
        List<String> violations
    ) {
        CurrentUserMessageBlock currentUserMessageBlock = blocks.stream()
            .filter(CurrentUserMessageBlock.class::isInstance)
            .map(CurrentUserMessageBlock.class::cast)
            .findFirst()
            .orElse(null);
        if (currentUserMessageBlock == null || currentUserMessageBlock.getCurrentUserMessage() == null) {
            violations.add("current user message block is missing");
            return;
        }
        if (modelMessages.isEmpty()) {
            return;
        }
        Message lastMessage = modelMessages.get(modelMessages.size() - 1);
        if (!Objects.equals(currentUserMessageBlock.getCurrentUserMessageId(), lastMessage.getMessageId())) {
            violations.add("current user message must be the last model message");
        }
    }

    private void validateDerivedMessagesNotInRecentRawMessages(List<ContextBlock> blocks, List<String> violations) {
        blocks.stream()
            .filter(RecentMessagesBlock.class::isInstance)
            .map(RecentMessagesBlock.class::cast)
            .flatMap(block -> block.getRawMessages().stream())
            .filter(this::isDerivedProjectionMessage)
            .findFirst()
            .ifPresent(message -> violations.add("derived projection message must not appear in RecentMessagesBlock.rawMessages"));
    }

    private void validateToolChain(List<Message> modelMessages, List<String> violations) {
        Queue<AssistantToolCall> pendingToolCalls = new LinkedList<>();
        for (Message message : modelMessages) {
            if (!pendingToolCalls.isEmpty()) {
                if (!(message instanceof ToolMessage toolMessage)) {
                    violations.add("assistant tool_calls must be followed by matching tool messages");
                    pendingToolCalls.clear();
                } else {
                    AssistantToolCall expectedToolCall = pendingToolCalls.remove();
                    if (!toolCallMatches(expectedToolCall, toolMessage)) {
                        violations.add("tool message does not match previous assistant tool_call");
                    }
                    continue;
                }
            }
            if (message instanceof ToolMessage) {
                violations.add("tool message cannot appear without a previous assistant tool_call");
                continue;
            }
            if (message instanceof AssistantMessage assistantMessage && !assistantMessage.getToolCalls().isEmpty()) {
                pendingToolCalls.addAll(assistantMessage.getToolCalls());
            }
        }
        if (!pendingToolCalls.isEmpty()) {
            violations.add("assistant tool_calls are not closed by tool messages");
        }
    }

    private boolean toolCallMatches(AssistantToolCall expectedToolCall, ToolMessage toolMessage) {
        return Objects.equals(expectedToolCall.getToolCallId(), toolMessage.getToolCallId())
            && Objects.equals(expectedToolCall.getToolCallRecordId(), toolMessage.getToolCallRecordId());
    }

    private void validateBudget(
        ContextBudgetSnapshot budget,
        WorkingContextProjection projection,
        List<String> violations
    ) {
        if (budget == null) {
            violations.add("context budget is missing");
            return;
        }
        if (Boolean.TRUE.equals(budget.getOverBudget())) {
            violations.add("context budget is over limit");
        }
        int availableInputTokens = budget.getModelMaxInputTokens()
            - budget.getReservedOutputTokens()
            - budget.getReservedToolLoopTokens()
            - budget.getSafetyMarginTokens();
        Integer inputTokenEstimate = projection.getInputTokenEstimate();
        if (inputTokenEstimate != null && inputTokenEstimate > availableInputTokens) {
            violations.add("projection inputTokenEstimate exceeds available input budget");
        }
    }

    private boolean isDerivedProjectionMessage(Message message) {
        if (message == null) {
            return false;
        }
        return message instanceof SystemMessage
            || message instanceof SummaryMessage
            || (message.getMessageId() != null && message.getMessageId().startsWith("ctxmsg-"));
    }

    private ProjectionValidationResult result(List<String> violations) {
        return ProjectionValidationResult.builder()
            .valid(violations.isEmpty())
            .violations(violations)
            .build();
    }
}
