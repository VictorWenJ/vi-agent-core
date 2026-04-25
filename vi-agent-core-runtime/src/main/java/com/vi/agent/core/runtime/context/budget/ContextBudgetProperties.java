package com.vi.agent.core.runtime.context.budget;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Context Kernel token budget configuration.
 */
@Getter
@Component
public class ContextBudgetProperties {

    private static final String INVALID_MESSAGE = "context budget configuration is invalid";

    @Value("${vi.agent.context.budget.model-max-input-tokens:8192}")
    private int modelMaxInputTokens;

    @Value("${vi.agent.context.budget.reserved-output-tokens:1024}")
    private int reservedOutputTokens;

    @Value("${vi.agent.context.budget.reserved-tool-loop-tokens:1024}")
    private int reservedToolLoopTokens;

    @Value("${vi.agent.context.budget.safety-margin-tokens:512}")
    private int safetyMarginTokens;

    public ContextBudgetProperties() {
    }

    public ContextBudgetProperties(
        int modelMaxInputTokens,
        int reservedOutputTokens,
        int reservedToolLoopTokens,
        int safetyMarginTokens
    ) {
        this.modelMaxInputTokens = modelMaxInputTokens;
        this.reservedOutputTokens = reservedOutputTokens;
        this.reservedToolLoopTokens = reservedToolLoopTokens;
        this.safetyMarginTokens = safetyMarginTokens;
        validate();
    }

    @PostConstruct
    public void validate() {
        if (modelMaxInputTokens <= 0
            || reservedOutputTokens <= 0
            || reservedToolLoopTokens <= 0
            || safetyMarginTokens <= 0) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, INVALID_MESSAGE);
        }
        int reservedTokens = reservedOutputTokens + reservedToolLoopTokens + safetyMarginTokens;
        if (reservedTokens >= modelMaxInputTokens) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, INVALID_MESSAGE);
        }
    }
}
