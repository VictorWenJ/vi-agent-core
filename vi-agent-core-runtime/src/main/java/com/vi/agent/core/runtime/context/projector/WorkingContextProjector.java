package com.vi.agent.core.runtime.context.projector;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.WorkingContext;
import com.vi.agent.core.model.context.WorkingContextProjection;
import com.vi.agent.core.model.context.block.ContextBlock;
import com.vi.agent.core.model.context.block.ConversationSummaryBlock;
import com.vi.agent.core.model.context.block.CurrentUserMessageBlock;
import com.vi.agent.core.model.context.block.RecentMessagesBlock;
import com.vi.agent.core.model.context.block.RuntimeInstructionBlock;
import com.vi.agent.core.model.context.block.SessionStateBlock;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.SummaryMessage;
import com.vi.agent.core.model.message.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Projects canonical WorkingContext blocks into provider-ready model messages.
 */
@Component
public class WorkingContextProjector {

    private final SyntheticMessageIdGenerator syntheticMessageIdGenerator;

    public WorkingContextProjector() {
        this(new SyntheticMessageIdGenerator());
    }

    @Autowired
    public WorkingContextProjector(SyntheticMessageIdGenerator syntheticMessageIdGenerator) {
        this.syntheticMessageIdGenerator = Objects.requireNonNull(syntheticMessageIdGenerator, "syntheticMessageIdGenerator must not be null");
    }

    public WorkingContextProjection project(WorkingContext context) {
        List<Message> modelMessages = new ArrayList<>();
        AtomicLong syntheticSequence = new AtomicLong(-1L);
        List<ContextBlock> blocks = context.getBlockSet().getOrderedBlocks().stream()
            .filter(block -> block != null && block.getDecision() == ContextAssemblyDecision.KEEP)
            .toList();

        blocks.stream()
            .filter(RuntimeInstructionBlock.class::isInstance)
            .map(RuntimeInstructionBlock.class::cast)
            .findFirst()
            .ifPresent(block -> modelMessages.add(SystemMessage.create(
                syntheticMessageIdGenerator.newSyntheticMessageId(MessageRole.SYSTEM, ContextBlockType.RUNTIME_INSTRUCTION),
                context.getMetadata().getConversationId(),
                context.getMetadata().getSessionId(),
                context.getMetadata().getTurnId(),
                context.getMetadata().getRunId(),
                syntheticSequence.getAndDecrement(),
                block.getRenderedText()
            )));

        blocks.stream()
            .filter(SessionStateBlock.class::isInstance)
            .map(SessionStateBlock.class::cast)
            .findFirst()
            .ifPresent(block -> modelMessages.add(SummaryMessage.create(
                syntheticMessageIdGenerator.newSyntheticMessageId(MessageRole.SUMMARY, ContextBlockType.SESSION_STATE),
                context.getMetadata().getConversationId(),
                context.getMetadata().getSessionId(),
                context.getMetadata().getTurnId(),
                context.getMetadata().getRunId(),
                syntheticSequence.getAndDecrement(),
                block.getRenderedText()
            )));

        blocks.stream()
            .filter(ConversationSummaryBlock.class::isInstance)
            .map(ConversationSummaryBlock.class::cast)
            .findFirst()
            .ifPresent(block -> modelMessages.add(SummaryMessage.create(
                syntheticMessageIdGenerator.newSyntheticMessageId(MessageRole.SUMMARY, ContextBlockType.CONVERSATION_SUMMARY),
                context.getMetadata().getConversationId(),
                context.getMetadata().getSessionId(),
                context.getMetadata().getTurnId(),
                context.getMetadata().getRunId(),
                syntheticSequence.getAndDecrement(),
                block.getRenderedText()
            )));

        blocks.stream()
            .filter(RecentMessagesBlock.class::isInstance)
            .map(RecentMessagesBlock.class::cast)
            .forEach(block -> modelMessages.addAll(block.getRawMessages()));

        blocks.stream()
            .filter(CurrentUserMessageBlock.class::isInstance)
            .map(CurrentUserMessageBlock.class::cast)
            .findFirst()
            .map(CurrentUserMessageBlock::getCurrentUserMessage)
            .ifPresent(modelMessages::add);

        return WorkingContextProjection.builder()
            .projectionId("wcp-" + UUID.randomUUID())
            .workingContextSnapshotId(context.getMetadata().getWorkingContextSnapshotId())
            .contextViewType(context.getMetadata().getContextViewType() == null
                ? ContextViewType.MAIN_AGENT
                : context.getMetadata().getContextViewType())
            .modelMessages(modelMessages)
            .inputTokenEstimate(context.getBudget() == null ? 0 : context.getBudget().getInputTokenEstimate())
            .build();
    }

}
