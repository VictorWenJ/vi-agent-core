package com.vi.agent.core.runtime.context.builder;

import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextSourceType;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextProjection;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.model.context.block.ConversationSummaryBlock;
import com.vi.agent.core.model.context.block.CurrentUserMessageBlock;
import com.vi.agent.core.model.context.block.RecentMessagesBlock;
import com.vi.agent.core.model.context.block.RuntimeInstructionBlock;
import com.vi.agent.core.model.context.block.SessionStateBlock;
import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConstraintRecord;
import com.vi.agent.core.model.memory.DecisionRecord;
import com.vi.agent.core.model.memory.OpenLoop;
import com.vi.agent.core.model.memory.OpenLoopKind;
import com.vi.agent.core.model.memory.OpenLoopStatus;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.memory.ToolOutcomeFreshnessPolicy;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.SummaryMessage;
import com.vi.agent.core.runtime.context.ContextTestFixtures;
import com.vi.agent.core.runtime.context.budget.ContextBudgetCalculator;
import com.vi.agent.core.runtime.context.budget.ContextBudgetProperties;
import com.vi.agent.core.runtime.context.loader.MemoryLoadBundle;
import com.vi.agent.core.runtime.context.loader.WorkingContextLoadCommand;
import com.vi.agent.core.runtime.context.projector.WorkingContextProjector;
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
        assertEquals("p2-c-v1", blocks.get(0).getSourceRefs().get(0).getSourceVersion());
        assertInstanceOf(SessionStateBlock.class, blocks.get(1));
        assertEquals("3", blocks.get(1).getSourceRefs().get(0).getSourceVersion());
        assertInstanceOf(ConversationSummaryBlock.class, blocks.get(2));
        assertEquals("4", blocks.get(2).getSourceRefs().get(0).getSourceVersion());
        RecentMessagesBlock recentMessagesBlock = (RecentMessagesBlock) blocks.get(3);
        assertEquals(List.of(recentMessage), recentMessagesBlock.getRawMessages());
        assertEquals("2", recentMessagesBlock.getSourceRefs().get(0).getSourceVersion());
        assertInstanceOf(CurrentUserMessageBlock.class, blocks.get(4));
        assertTrue(blocks.get(4).isRequired());
        assertEquals("10", blocks.get(4).getSourceRefs().get(0).getSourceVersion());
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

    @Test
    void buildBlocksShouldRenderConcreteSessionStateAndProjectIt() {
        ContextBlockFactory factory = new ContextBlockFactory(new ContextBudgetCalculator(new ContextBudgetProperties(500, 20, 20, 10)));
        MemoryLoadBundle bundle = MemoryLoadBundle.builder()
            .workingSetVersion(2L)
            .recentRawMessages(List.of())
            .latestState(detailedState())
            .currentUserMessage(ContextTestFixtures.currentUserMessage())
            .agentMode(com.vi.agent.core.model.context.AgentMode.GENERAL)
            .contextViewType(com.vi.agent.core.model.context.ContextViewType.MAIN_AGENT)
            .build();

        List<ContextBlock> blocks = factory.buildBlocks(loadCommand(), bundle);
        SessionStateBlock stateBlock = (SessionStateBlock) blocks.get(1);
        WorkingContext context = ContextTestFixtures.context(blocks, ContextTestFixtures.budget(80));
        WorkingContextProjection projection = new WorkingContextProjector().project(context);
        SummaryMessage stateMessage = (SummaryMessage) projection.getModelMessages().get(1);

        assertEquals(ContextSourceType.SESSION_STATE_SNAPSHOT, stateBlock.getSourceRefs().get(0).getSourceType());
        assertEquals("5", stateBlock.getSourceRefs().get(0).getSourceVersion());
        assertTrue(stateBlock.getRenderedText().contains("Fact content for prompt."));
        assertTrue(stateBlock.getRenderedText().contains("Constraint content for prompt."));
        assertTrue(stateBlock.getRenderedText().contains("Decision text."));
        assertTrue(stateBlock.getRenderedText().contains("[OPEN] FOLLOW_UP_ACTION - Open loop content."));
        assertTrue(stateBlock.getRenderedText().contains("toolA - Tool summary text."));
        assertTrue(stateMessage.getContentText().contains("Fact content for prompt."));
        assertTrue(stateMessage.getContentText().contains("Constraint content for prompt."));
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

    private SessionStateSnapshot detailedState() {
        return SessionStateSnapshot.builder()
            .snapshotId("state-detailed")
            .sessionId(ContextTestFixtures.SESSION_ID)
            .stateVersion(5L)
            .taskGoal("Detailed goal")
            .confirmedFact(ConfirmedFactRecord.builder()
                .factId("fact-1")
                .content("Fact content for prompt.")
                .build())
            .constraint(ConstraintRecord.builder()
                .constraintId("constraint-1")
                .content("Constraint content for prompt.")
                .build())
            .decision(DecisionRecord.builder()
                .decisionId("decision-1")
                .content("Decision text.")
                .build())
            .openLoop(OpenLoop.builder()
                .loopId("loop-1")
                .kind(OpenLoopKind.FOLLOW_UP_ACTION)
                .status(OpenLoopStatus.OPEN)
                .content("Open loop content.")
                .sourceType("USER")
                .sourceRef("msg-user-1")
                .build())
            .recentToolOutcome(ToolOutcomeDigest.builder()
                .digestId("digest-1")
                .toolCallRecordId("tcr-1")
                .toolExecutionId("tex-1")
                .toolName("toolA")
                .summary("Tool summary text.")
                .freshnessPolicy(ToolOutcomeFreshnessPolicy.SESSION)
                .build())
            .build();
    }
}
