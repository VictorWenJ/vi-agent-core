package com.vi.agent.core.runtime.context.loader;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Memory inputs used by the Context Kernel first-call builder.
 */
@Getter
@Builder
public class MemoryLoadBundle {

    private final SessionWorkingSetSnapshot sessionWorkingSetSnapshot;

    private final Long workingSetVersion;

    @Singular("recentRawMessage")
    private final List<Message> recentRawMessages;

    private final SessionStateSnapshot latestState;

    private final ConversationSummary latestSummary;

    private final UserMessage currentUserMessage;

    private final AgentMode agentMode;

    private final ContextViewType contextViewType;
}
