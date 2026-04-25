package com.vi.agent.core.runtime.context.projector;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
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
import com.vi.agent.core.model.message.SummaryMessage;
import com.vi.agent.core.model.message.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Projects canonical WorkingContext blocks into provider-ready model messages.
 */
@Component
public class WorkingContextProjector {

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
            .map(block -> SystemMessage.create(
                nextSyntheticMessageId("runtime"),
                context.getMetadata().getConversationId(),
                context.getMetadata().getSessionId(),
                context.getMetadata().getTurnId(),
                context.getMetadata().getRunId(),
                syntheticSequence.getAndDecrement(),
                block.getRenderedText()
            ))
            .ifPresent(modelMessages::add);

        blocks.stream()
            .filter(SessionStateBlock.class::isInstance)
            .map(SessionStateBlock.class::cast)
            .findFirst()
            .map(block -> SummaryMessage.create(
                nextSyntheticMessageId("state"),
                context.getMetadata().getConversationId(),
                context.getMetadata().getSessionId(),
                context.getMetadata().getTurnId(),
                context.getMetadata().getRunId(),
                syntheticSequence.getAndDecrement(),
                block.getRenderedText()
            ))
            .ifPresent(modelMessages::add);

        blocks.stream()
            .filter(ConversationSummaryBlock.class::isInstance)
            .map(ConversationSummaryBlock.class::cast)
            .findFirst()
            .map(block -> SummaryMessage.create(
                nextSyntheticMessageId("summary"),
                context.getMetadata().getConversationId(),
                context.getMetadata().getSessionId(),
                context.getMetadata().getTurnId(),
                context.getMetadata().getRunId(),
                syntheticSequence.getAndDecrement(),
                block.getRenderedText()
            ))
            .ifPresent(modelMessages::add);

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

    private String nextSyntheticMessageId(String messageKind) {
        return "ctxmsg-" + messageKind + "-" + UUID.randomUUID();
    }
}
