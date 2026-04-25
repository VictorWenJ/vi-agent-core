package com.vi.agent.core.model.context;

import com.vi.agent.core.model.context.block.ContextBlock;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * ContextPlanner 对 block 的预算与装配决策。
 */
@Getter
@Builder
public class ContextPlan {

    /** 计划装配的上下文 block 列表。 */
    @Singular("block")
    private final List<ContextBlock> blocks;

    /** 本次计划的 token 预算快照。 */
    private final ContextBudgetSnapshot budget;

    /** 计划是否超出 token 预算。 */
    private final Boolean overBudget;
}
