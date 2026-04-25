package com.vi.agent.core.runtime.context.builder;

import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.model.context.block.ConversationSummaryBlock;
import com.vi.agent.core.model.context.block.CurrentUserMessageBlock;
import com.vi.agent.core.model.context.block.RecentMessagesBlock;
import com.vi.agent.core.model.context.block.RuntimeInstructionBlock;
import com.vi.agent.core.model.context.block.SessionStateBlock;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.runtime.context.ContextTestFixtures;
import com.vi.agent.core.runtime.context.budget.ContextBudgetCalculator;
import com.vi.agent.core.runtime.context.budget.ContextBudgetProperties;
import com.vi.agent.core.runtime.context.loader.MemoryLoadBundle;
import com.vi.agent.core.runtime.context.loader.WorkingContextLoadCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBlockFactoryTest {

    @Test
    void buildBlocksShouldCreateRequiredBlocksAndKeepRawRecentMessagesOnly() {
        ContextBlockFactory factory = new ContextBlockFactory(new ContextBudgetCalculator(new ContextBudgetProperties(200, 20, 20, 10)));
        Message recentMessage = ContextTestFixtures.recentUserMessage();
        MemoryLoadBundle bundle = MemoryLoadBundle.builder()
            .workingSetVersion(2L)
            .recentRawMessages(List.of(recentMessage))
            .latestState(ContextTestFixtures.stateSnapshot())
            .latestSummary(ContextTestFixtures.summary())
            .currentUserMessage(ContextTestFixtures.currentUserMessage())
            .agentMode(com.vi.agent.core.model.context.AgentMode.GENERAL)
            .contextViewType(com.vi.agent.core.model.context.ContextViewType.MAIN_AGENT)
            .build();

        List<ContextBlock> blocks = factory.buildBlocks(loadCommand(), bundle);

        assertEquals(List.of(
            ContextBlockType.RUNTIME_INSTRUCTION,
            ContextBlockType.SESSION_STATE,
            ContextBlockType.CONVERSATION_SUMMARY,
            ContextBlockType.RECENT_MESSAGES,
            ContextBlockType.CURRENT_USER_MESSAGE
        ), blocks.stream().map(ContextBlock::getBlockType).toList());
        assertInstanceOf(RuntimeInstructionBlock.class, blocks.get(0));
        assertTrue(blocks.get(0).isRequired());
        assertInstanceOf(SessionStateBlock.class, blocks.get(1));
        assertInstanceOf(ConversationSummaryBlock.class, blocks.get(2));
        RecentMessagesBlock recentMessagesBlock = (RecentMessagesBlock) blocks.get(3);
        assertEquals(List.of(recentMessage), recentMessagesBlock.getRawMessages());
        assertInstanceOf(CurrentUserMessageBlock.class, blocks.get(4));
        assertTrue(blocks.get(4).isRequired());
    }

    @Test
    void buildBlocksShouldOmitStateAndSummaryWhenAbsent() {
        ContextBlockFactory factory = new ContextBlockFactory(new ContextBudgetCalculator(new ContextBudgetProperties(200, 20, 20, 10)));
        MemoryLoadBundle bundle = MemoryLoadBundle.builder()
            .workingSetVersion(2L)
            .recentRawMessages(List.of())
            .currentUserMessage(ContextTestFixtures.currentUserMessage())
            .agentMode(com.vi.agent.core.model.context.AgentMode.GENERAL)
            .contextViewType(com.vi.agent.core.model.context.ContextViewType.MAIN_AGENT)
            .build();

        List<ContextBlock> blocks = factory.buildBlocks(loadCommand(), bundle);

        assertEquals(List.of(
            ContextBlockType.RUNTIME_INSTRUCTION,
            ContextBlockType.RECENT_MESSAGES,
            ContextBlockType.CURRENT_USER_MESSAGE
        ), blocks.stream().map(ContextBlock::getBlockType).toList());
    }

    private WorkingContextLoadCommand loadCommand() {
        return WorkingContextLoadCommand.builder()
            .conversationId(ContextTestFixtures.CONVERSATION_ID)
            .sessionId(ContextTestFixtures.SESSION_ID)
            .turnId(ContextTestFixtures.TURN_ID)
            .runId(ContextTestFixtures.RUN_ID)
            .currentUserMessage(ContextTestFixtures.currentUserMessage())
            .agentMode(com.vi.agent.core.model.context.AgentMode.GENERAL)
            .contextViewType(com.vi.agent.core.model.context.ContextViewType.MAIN_AGENT)
            .checkpointTrigger(com.vi.agent.core.model.context.CheckpointTrigger.BEFORE_FIRST_MODEL_CALL)
            .modelCallSequenceNo(1)
            .build();
    }
}
