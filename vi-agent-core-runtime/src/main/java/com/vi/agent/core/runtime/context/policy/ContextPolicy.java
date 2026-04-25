package com.vi.agent.core.runtime.context.policy;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.block.ContextBlock;

import java.util.List;

public interface ContextPolicy {

    List<ContextBlock> rankBlocks(List<ContextBlock> blocks, AgentMode agentMode);

    List<ContextAssemblyDecision> decide(List<ContextBlock> blocks, ContextBudgetSnapshot budget, ContextViewType viewType);
}
