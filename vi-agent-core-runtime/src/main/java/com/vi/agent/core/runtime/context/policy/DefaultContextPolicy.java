package com.vi.agent.core.runtime.context.policy;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.block.ContextBlock;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GENERAL mode context policy for the P2-C first implementation.
 */
@Component
public class DefaultContextPolicy implements ContextPolicy {

    private static final Map<ContextBlockType, Integer> RANKS = Map.of(
        ContextBlockType.RUNTIME_INSTRUCTION, 0,
        ContextBlockType.CURRENT_USER_MESSAGE, 1,
        ContextBlockType.SESSION_STATE, 2,
        ContextBlockType.RECENT_MESSAGES, 3,
        ContextBlockType.CONVERSATION_SUMMARY, 4,
        ContextBlockType.CONTEXT_REFERENCE, 5,
        ContextBlockType.COMPACTION_NOTE, 6
    );

    @Override
    public List<ContextBlock> rankBlocks(List<ContextBlock> blocks, AgentMode agentMode) {
        if (CollectionUtils.isEmpty(blocks)) {
            return List.of();
        }
        return blocks.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(block -> RANKS.getOrDefault(block.getBlockType(), Integer.MAX_VALUE)))
            .toList();
    }

    @Override
    public List<ContextAssemblyDecision> decide(List<ContextBlock> blocks, ContextBudgetSnapshot budget, ContextViewType viewType) {
        if (CollectionUtils.isEmpty(blocks)) {
            return List.of();
        }
        return blocks.stream().map(block -> ContextAssemblyDecision.KEEP).toList();
    }
}
