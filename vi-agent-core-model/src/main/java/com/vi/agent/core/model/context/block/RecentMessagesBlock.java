package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.message.Message;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * S3 recent raw transcript 消息块。
 */
@Getter
public class RecentMessagesBlock extends ContextBlock {

    /** working set 版本。 */
    private final Long workingSetVersion;

    /** recent message ID 列表。 */
    private final List<String> messageIds;

    /** recent raw transcript 消息列表。 */
    private final List<Message> rawMessages;

    @Builder
    private RecentMessagesBlock(
        String blockId,
        ContextPriority priority,
        boolean required,
        Integer tokenEstimate,
        ContextAssemblyDecision decision,
        List<ContextSourceRef> sourceRefs,
        List<String> evidenceIds,
        Long workingSetVersion,
        List<String> messageIds,
        List<Message> rawMessages
    ) {
        super(blockId, ContextBlockType.RECENT_MESSAGES, priority, required, tokenEstimate, decision, sourceRefs, evidenceIds);
        this.workingSetVersion = workingSetVersion;
        this.messageIds = messageIds == null || messageIds.isEmpty() ? List.of() : List.copyOf(messageIds);
        this.rawMessages = rawMessages == null || rawMessages.isEmpty() ? List.of() : List.copyOf(rawMessages);
    }
}
