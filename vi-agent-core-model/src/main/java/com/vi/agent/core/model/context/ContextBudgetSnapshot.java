package com.vi.agent.core.model.context;

import lombok.Builder;
import lombok.Getter;

/**
 * 单次上下文构建的 token 预算快照。
 */
@Getter
@Builder
public class ContextBudgetSnapshot {

    /** 模型输入 token 上限。 */
    private final Integer modelInputTokenLimit;

    /** 为模型输出预留的 token 数。 */
    private final Integer reservedOutputTokens;

    /** 为工具循环预留的 token 数。 */
    private final Integer reservedToolLoopTokens;

    /** 安全边际 token 数。 */
    private final Integer safetyMarginTokens;

    /** 可用于上下文装配的 token 数。 */
    private final Integer availableContextTokens;

    /** 本次上下文预计消耗的 token 数。 */
    private final Integer estimatedContextTokens;

    /** 是否超出预算。 */
    private final Boolean overBudget;
}
