package com.vi.agent.core.runtime.context;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBudgetSnapshot;
import com.vi.agent.core.model.context.ContextPlan;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.ProjectionValidationResult;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextBuildResult;
import com.vi.agent.core.model.context.WorkingContextMetadata;
import com.vi.agent.core.model.context.WorkingContextProjection;
import com.vi.agent.core.model.context.WorkingContextSource;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.model.context.block.ContextBlockSet;
import com.vi.agent.core.model.context.block.ConversationSummaryBlock;
import com.vi.agent.core.model.context.block.CurrentUserMessageBlock;
import com.vi.agent.core.model.context.block.RecentMessagesBlock;
import com.vi.agent.core.model.context.block.RuntimeInstructionBlock;
import com.vi.agent.core.model.context.block.SessionStateBlock;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.SystemMessage;
import com.vi.agent.core.model.message.UserMessage;

import java.time.Instant;
import java.util.List;

public final class ContextTestFixtures {

    public static final String CONVERSATION_ID = "conv-1";
    public static final String SESSION_ID = "sess-1";
    public static final String TURN_ID = "turn-1";
    public static final String RUN_ID = "run-1";

    private ContextTestFixtures() {
    }

    public static UserMessage currentUserMessage() {
        return UserMessage.create("msg-current", CONVERSATION_ID, SESSION_ID, TURN_ID, RUN_ID, 10L, "current user");
    }

    public static UserMessage recentUserMessage() {
        return UserMessage.create("msg-recent", CONVERSATION_ID, SESSION_ID, "turn-0", RUN_ID, 1L, "recent user");
    }

    public static SystemMessage runtimeSystemMessage() {
        return SystemMessage.create("ctxmsg-runtime", CONVERSATION_ID, SESSION_ID, TURN_ID, RUN_ID, -1L, "runtime");
    }

    public static SessionStateSnapshot stateSnapshot() {
        return SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId(SESSION_ID)
            .stateVersion(3L)
            .taskGoal("finish task")
            .updatedAt(Instant.now())
            .build();
    }

    public static ConversationSummary summary() {
        return ConversationSummary.builder()
            .summaryId("sum-1")
            .sessionId(SESSION_ID)
            .summaryVersion(4L)
            .coveredFromSequenceNo(1L)
            .coveredToSequenceNo(9L)
            .summaryText("conversation summary")
            .summaryTemplateKey("summary")
            .summaryTemplateVersion("v1")
            .createdAt(Instant.now())
            .build();
    }

    public static RuntimeInstructionBlock runtimeBlock() {
        return RuntimeInstructionBlock.builder()
            .blockId("blk-runtime")
            .priority(ContextPriority.MANDATORY)
            .required(true)
            .tokenEstimate(10)
            .decision(ContextAssemblyDecision.KEEP)
            .renderedText("runtime")
            .build();
    }

    public static SessionStateBlock stateBlock() {
        return SessionStateBlock.builder()
            .blockId("blk-state")
            .priority(ContextPriority.HIGH)
            .required(false)
            .tokenEstimate(10)
            .decision(ContextAssemblyDecision.KEEP)
            .stateVersion(3L)
            .stateSnapshot(stateSnapshot())
            .renderedText("state render")
            .build();
    }

    public static ConversationSummaryBlock summaryBlock() {
        return ConversationSummaryBlock.builder()
            .blockId("blk-summary")
            .priority(ContextPriority.MEDIUM)
            .required(false)
            .tokenEstimate(10)
            .decision(ContextAssemblyDecision.KEEP)
            .summaryVersion(4L)
            .summary(summary())
            .renderedText("summary render")
            .build();
    }

    public static RecentMessagesBlock recentMessagesBlock(List<Message> messages) {
        return RecentMessagesBlock.builder()
            .blockId("blk-recent")
            .priority(ContextPriority.HIGH)
            .required(false)
            .tokenEstimate(10)
            .decision(ContextAssemblyDecision.KEEP)
            .workingSetVersion(2L)
            .messageIds(messages.stream().map(Message::getMessageId).toList())
            .rawMessages(messages)
            .build();
    }

    public static CurrentUserMessageBlock currentUserBlock() {
        return CurrentUserMessageBlock.builder()
            .blockId("blk-current")
            .priority(ContextPriority.MANDATORY)
            .required(true)
            .tokenEstimate(10)
            .decision(ContextAssemblyDecision.KEEP)
            .currentUserMessageId(currentUserMessage().getMessageId())
            .currentUserMessage(currentUserMessage())
            .build();
    }

    public static ContextBudgetSnapshot budget(int inputTokenEstimate) {
        int remainingBudget = 200 - 20 - 20 - 10 - inputTokenEstimate;
        return ContextBudgetSnapshot.builder()
            .modelMaxInputTokens(200)
            .inputTokenEstimate(inputTokenEstimate)
            .reservedOutputTokens(20)
            .reservedToolLoopTokens(20)
            .safetyMarginTokens(10)
            .remainingBudget(remainingBudget)
            .overBudget(remainingBudget < 0)
            .build();
    }

    public static WorkingContext context(List<ContextBlock> blocks, ContextBudgetSnapshot budget) {
        return WorkingContext.builder()
            .metadata(WorkingContextMetadata.builder()
                .workingContextSnapshotId("wctx-1")
                .conversationId(CONVERSATION_ID)
                .sessionId(SESSION_ID)
                .turnId(TURN_ID)
                .runId(RUN_ID)
                .contextBuildSeq(1)
                .modelCallSequenceNo(1)
                .checkpointTrigger(CheckpointTrigger.BEFORE_FIRST_MODEL_CALL)
                .contextViewType(ContextViewType.MAIN_AGENT)
                .agentMode(AgentMode.GENERAL)
                .build())
            .source(WorkingContextSource.builder()
                .workingSetVersion(2L)
                .stateVersion(3L)
                .summaryVersion(4L)
                .build())
            .budget(budget)
            .blockSet(ContextBlockSet.of(blocks))
            .build();
    }

    public static WorkingContextBuildResult buildResult(WorkingContext context, WorkingContextProjection projection) {
        return WorkingContextBuildResult.builder()
            .context(context)
            .projection(projection)
            .contextPlan(ContextPlan.builder()
                .blocks(context.getBlockSet().getOrderedBlocks())
                .budget(context.getBudget())
                .overBudget(context.getBudget().getOverBudget())
                .build())
            .validationResult(ProjectionValidationResult.builder().valid(true).build())
            .build();
    }
}
