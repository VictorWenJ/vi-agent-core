package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.message.Message;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 当前用户消息保底块。
 */
@Getter
public class CurrentUserMessageBlock extends ContextBlock {

    /** 当前用户消息 ID。 */
    private final String currentUserMessageId;

    /** 当前用户消息对象。 */
    private final Message currentUserMessage;

    @Builder
    private CurrentUserMessageBlock(
        String blockId,
        ContextPriority priority,
        boolean required,
        Integer tokenEstimate,
        ContextAssemblyDecision decision,
        List<ContextSourceRef> sourceRefs,
        List<String> evidenceIds,
        String currentUserMessageId,
        Message currentUserMessage
    ) {
        super(blockId, ContextBlockType.CURRENT_USER_MESSAGE, priority, required, tokenEstimate, decision, sourceRefs, evidenceIds);
        this.currentUserMessageId = currentUserMessageId;
        this.currentUserMessage = currentUserMessage;
    }
}
