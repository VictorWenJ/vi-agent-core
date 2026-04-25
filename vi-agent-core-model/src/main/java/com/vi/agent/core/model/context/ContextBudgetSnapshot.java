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
    private final Integer modelMaxInputTokens;

    /** 当前 projection 估算输入 token。 */
    private final Integer inputTokenEstimate;

    /** 为模型输出预留的 token 数。 */
    private final Integer reservedOutputTokens;

    /** 为工具循环预留的 token 数。 */
    private final Integer reservedToolLoopTokens;

    /** 安全边际 token 数。 */
    private final Integer safetyMarginTokens;

    /** 剩余可用预算。 */
    private final Integer remainingBudget;

    /** 是否超出预算。 */
    private final Boolean overBudget;
}
