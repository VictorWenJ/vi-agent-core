package com.vi.agent.core.runtime.context.budget;

import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.model.message.Message;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Calculates the P2-C input token budget snapshot with a simple estimator.
 */
@Component
public class ContextBudgetCalculator {

    private static final int TEXT_TOKEN_OVERHEAD = 4;

    private final ContextBudgetProperties properties;

    public ContextBudgetCalculator(ContextBudgetProperties properties) {
        this.properties = properties;
    }

    public ContextBudgetSnapshot calculateForInputTokenEstimate(int inputTokenEstimate) {
        int remainingBudget = properties.getModelMaxInputTokens()
            - properties.getReservedOutputTokens()
            - properties.getReservedToolLoopTokens()
            - properties.getSafetyMarginTokens()
            - inputTokenEstimate;
        return ContextBudgetSnapshot.builder()
            .modelMaxInputTokens(properties.getModelMaxInputTokens())
            .inputTokenEstimate(inputTokenEstimate)
            .reservedOutputTokens(properties.getReservedOutputTokens())
            .reservedToolLoopTokens(properties.getReservedToolLoopTokens())
            .safetyMarginTokens(properties.getSafetyMarginTokens())
            .remainingBudget(remainingBudget)
            .overBudget(remainingBudget < 0)
            .build();
    }

    public ContextBudgetSnapshot calculateBlocks(List<ContextBlock> blocks) {
        int inputTokenEstimate = blocks ==
            null ? 0 : blocks.stream()
                       .filter(block -> block != null && block.getTokenEstimate() != null)
                       .mapToInt(ContextBlock::getTokenEstimate)
                       .sum();
        return calculateForInputTokenEstimate(inputTokenEstimate);
    }

    public int estimateMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream().mapToInt(this::estimateMessage).sum();
    }

    public int estimateMessage(Message message) {
        if (message == null) {
            return 0;
        }
        return estimateText(message.getContentText());
    }

    public int estimateText(String text) {
        if (StringUtils.isBlank(text)) {
            return TEXT_TOKEN_OVERHEAD;
        }
        return Math.max(1, text.length() / 4) + TEXT_TOKEN_OVERHEAD;
    }
}
