package com.vi.agent.core.runtime.context.planner;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import com.vi.agent.core.model.context.ContextPlan;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.runtime.context.policy.ContextPolicy;
import com.vi.agent.core.runtime.context.policy.ContextPolicyResolver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the ordered ContextPlan without P2-D compaction.
 */
@Component
public class ContextPlanner {

    private final ContextPolicyResolver contextPolicyResolver;

    public ContextPlanner(ContextPolicyResolver contextPolicyResolver) {
        this.contextPolicyResolver = contextPolicyResolver;
    }

    public ContextPlan plan(List<ContextBlock> blocks, ContextBudgetSnapshot budget, AgentMode agentMode, ContextViewType viewType) {
        ContextPolicy contextPolicy = contextPolicyResolver.getPolicyByAgentMode(agentMode);
        List<ContextBlock> rankedBlocks = contextPolicy.rankBlocks(blocks, agentMode);
        List<ContextAssemblyDecision> decisions = contextPolicy.decide(rankedBlocks, budget, viewType);
        List<ContextBlock> finalBlocks = rankedBlocks;
        if (decisions.size() != rankedBlocks.size()) {
            finalBlocks = rankedBlocks.stream().filter(ContextBlock::isRequired).toList();
        }
        return ContextPlan.builder()
            .blocks(finalBlocks)
            .budget(budget)
            .overBudget(budget == null ? null : budget.getOverBudget())
            .build();
    }
}
