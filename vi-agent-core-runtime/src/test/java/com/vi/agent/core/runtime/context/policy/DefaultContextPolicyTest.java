package com.vi.agent.core.runtime.context.policy;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.runtime.context.ContextTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultContextPolicyTest {

    @Test
    void generalModeShouldRankBlocksByP2CPriorityOrder() {
        DefaultContextPolicy policy = new DefaultContextPolicy();
        List<ContextBlock> unorderedBlocks = List.of(
            ContextTestFixtures.summaryBlock(),
            ContextTestFixtures.recentMessagesBlock(List.of(ContextTestFixtures.recentUserMessage())),
            ContextTestFixtures.currentUserBlock(),
            ContextTestFixtures.stateBlock(),
            ContextTestFixtures.runtimeBlock()
        );

        List<ContextBlock> ranked = policy.rankBlocks(unorderedBlocks, AgentMode.GENERAL);

        assertEquals(List.of(
            "blk-runtime",
            "blk-current",
            "blk-state",
            "blk-recent",
            "blk-summary"
        ), ranked.stream().map(ContextBlock::getBlockId).toList());
    }

    @Test
    void mandatoryBlocksShouldKeep() {
        DefaultContextPolicy policy = new DefaultContextPolicy();

        List<ContextAssemblyDecision> decisions = policy.decide(
            List.of(ContextTestFixtures.runtimeBlock(), ContextTestFixtures.currentUserBlock()),
            ContextTestFixtures.budget(20),
            ContextViewType.MAIN_AGENT
        );

        assertEquals(List.of(ContextAssemblyDecision.KEEP, ContextAssemblyDecision.KEEP), decisions);
        assertTrue(List.of(ContextTestFixtures.runtimeBlock(), ContextTestFixtures.currentUserBlock()).stream()
            .filter(ContextBlock::isRequired)
            .allMatch(block -> block.getPriority() == ContextPriority.MANDATORY));
    }

    @Test
    void resolverShouldReturnDefaultGeneralPolicyOnly() {
        ContextPolicyResolver resolver = new ContextPolicyResolver(new DefaultContextPolicy());

        assertInstanceOf(DefaultContextPolicy.class, resolver.getPolicyByAgentMode(AgentMode.GENERAL));
    }
}
